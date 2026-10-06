package br.commons.persistence.jdbc;

import br.commons.i18n.Errors;

import br.commons.Logger;
import br.commons.Result;
import br.commons.persistence.jdbc.pool.ConnectionPool;
import br.commons.persistence.jdbc.primitives.JDBCConnection;
import br.commons.persistence.jdbc.primitives.JDBCParameter;
import br.commons.persistence.jdbc.primitives.JDBCResultSet;
import lombok.Getter;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a named JDBC data source with connection pooling capabilities.
 * This class wraps a ConnectionPool and provides simplified access to database connections.
 */
@NullMarked
public class DataSource implements Transactions {

    @Getter
    private final String name;
    private final ConnectionPool pool;
    private final TransactionInitializer initializer;
    private volatile boolean closed;

    /** Active transactions */
    static final Map<String, ThreadLocal<JDBCTransaction>> transactions = new ConcurrentHashMap<>();

    /**
     * Live transactions of this DataSource across all threads. {@link #transactions} is ThreadLocal —
     * one thread never sees another's slot —, so {@link #close()} would have no way to report
     * orphan transactions without this parallel registry. Enters in {@link #begin(String, long)} and exits in
     * {@link JDBCTransaction#close()} (via {@link #unregister(JDBCTransaction)}).
     */
    private final Set<JDBCTransaction> live = ConcurrentHashMap.newKeySet();

    /**
     * Outermost transactions open on this thread, most recent first, whatever their name: {@link #afterCommit}
     * attaches to the top one. Looking only at {@code "default"} ran the action at once inside a named
     * transaction — before its commit.
     */
    private final ThreadLocal<Deque<JDBCTransaction>> open = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Creates a new DataSource with the specified properties.
     *
     * @param properties Configuration properties for this data source
     * @throws IllegalArgumentException if properties are invalid
     * @throws RuntimeException if pool initialization fails
     */
    public DataSource(JDBCProperties properties) {
        this(properties, TransactionInitializer.NONE);
    }

    /** A data source whose outermost transactions start with {@code initializer} (spec §4.3). */
    public DataSource(JDBCProperties properties, TransactionInitializer initializer) {
        this.name = properties.name();
        this.initializer = initializer;
        this.pool = new ConnectionPool(properties);
        this.closed = false;

        Logger.trace("DataSource '%s' created", name);
    }

    public Result<JDBCTransaction, String> begin() {
        return begin("default");
    }

    public Result<JDBCTransaction, String> begin(String name) {
       return begin(name, pool.properties().connectionTimeout());
    }

    public Result<JDBCTransaction, String> begin(long timeout) {
        return begin("default", timeout);
    }

    public Result<JDBCTransaction, String> begin(String name, long timeout) {
        // One transaction per name AND per thread: each thread has its own slot in ThreadLocal.
        // Reentrancy in the SAME thread returns the running transaction (re-obtained/continued); different
        // threads never share the same transaction/connection (race condition from the old model, which captured
        // a single instance in withInitial and served it to all threads).
        val holder = transactions.computeIfAbsent(name, n -> new ThreadLocal<>());

        // Do not remove this creation branch: on the thread's first call the slot is empty
        // (holder.get() == null). Without it, begin() would return Result.success(null), and readOnly()/
        // mutating() would immediately fall on Logger.fatal("Transaction is null") → every database operation
        // would break. This is where the connection is acquired and the transaction is bound to the thread's slot.
        val existing = holder.get();
        if (existing != null) {
            // REQUIRED propagation: the nested block participates in the running transaction. The level is counted
            // so that only the close() of the outermost block returns the connection to the pool.
            existing.enter();
            return Result.success(existing);
        }

        Logger.trace("Initializing transaction '%s' with timeout: %d", name, timeout);
        return getConnection(timeout).flatMap(connection -> {
            val transaction = new JDBCTransaction(this, name, connection);
            holder.set(transaction);
            live.add(transaction);
            open.get().push(transaction);
            return initialize(transaction);
        });
    }

    private Result<JDBCTransaction, String> initialize(JDBCTransaction transaction) {
        return switch (initializer.initialize(transaction)) {
            case Result.Success<Void, String> ignored -> Result.success(transaction);
            case Result.Failure<Void, String>(var error) -> {
                transaction.rollback();
                transaction.close();
                yield Result.failure(Errors.get().transactionInitializerFailed(error).value());
            }
        };
    }

    @Override
    public <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work) {
        val transaction = switch (begin()) {
            case Result.Success<JDBCTransaction, String>(var opened) -> opened;
            case Result.Failure<JDBCTransaction, String>(var error) -> throw new IllegalStateException(error);
        };
        try {
            val result = work.get();
            if (result.isSuccess()) {
                transaction.commit().ifFailure(error -> { throw new IllegalStateException(error); });
            } else {
                transaction.markRollbackOnly();
                transaction.rollback();
            }
            return result;
        } catch (RuntimeException e) {
            transaction.markRollbackOnly();
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
        }
    }

    @Override
    public void afterCommit(Runnable action) {
        val current = open.get().peek();
        if (current == null) {
            action.run();
        } else {
            current.afterCommit(action);
        }
    }

    /** Removes the transaction from the live registry. Called by {@link JDBCTransaction#close()}. */
    void unregister(JDBCTransaction transaction) {
        live.remove(transaction);
        open.get().remove(transaction);
    }

    /**
     * Acquires a connection from the pool using the default timeout.
     *
     * @return Result containing a pooled database connection or error message
     */
    private Result<JDBCConnection, String> getConnection() {
        Logger.trace("Acquiring connection");
        if (closed) return Results.resourceIsClosed(name);
        return pool.aquire();
    }

    /**
     * Acquires a connection from the pool with a custom timeout.
     *
     * @param timeoutMs Maximum time to wait for a connection in milliseconds
     * @return Result containing a pooled database connection or error message
     */
    private Result<JDBCConnection, String> getConnection(long timeoutMs) {
        Logger.trace("Acquiring connection with timeout: %d", timeoutMs);
        if (closed) return Results.resourceIsClosed(name);
        return pool.aquire(timeoutMs);
    }

    /**
     * The transaction for a {@link #query} or {@link #execute}: a failure to open it is a failure of the call
     * (no database, pool exhausted), thrown as {@link IllegalStateException} with the cause's message.
     */
    private JDBCTransaction opened() {
        return switch (begin()) {
            case Result.Failure<JDBCTransaction, String>(var error) -> throw new IllegalStateException(error);
            case Result.Success<JDBCTransaction, String>(var transaction) -> {
                if (transaction == null) throw new IllegalStateException(Errors.get().nullTransaction(name).value());
                yield transaction;
            }
        };
    }

    /** Value of a successful work; a {@code Failure} becomes {@link IllegalStateException} with its message. */
    private static <T> T valueOf(Result<T, String> result) {
        return switch (result) {
            case Result.Success<T, String>(var value) -> value;
            case Result.Failure<T, String>(var error) -> throw new IllegalStateException(error);
        };
    }

    private <T> T readOnly(Function<JDBCTransaction, Result<T, String>> work) {
        val transaction = opened();
        final Result<T, String> result;
        try {
            result = work.apply(transaction);
            // Only rolls back if it's the outermost level: a nested read within a write
            // cannot discard the work in progress (nor mark rollbackOnly — it's not a failure).
            transaction.rollback();
        } finally { transaction.close(); }
        return valueOf(result);
    }

    /**
     * Runs {@code work} and commits, or rolls back on {@code Failure} or exception. An exception thrown by
     * {@code work} reaches the caller unchanged (type and cause), after the rollback.
     */
    private <T> T mutating(Function<JDBCTransaction, Result<T, String>> work) {
        val transaction = opened();
        final Result<T, String> result;
        try {
            result = work.apply(transaction);
            if (result.isSuccess()) {
                if (transaction.commit() instanceof Result.Failure<Boolean, String>(var error)) {
                    throw new IllegalStateException(Errors.get().commitFailed(error).value());
                }
            } else {
                // Nested: commit()/rollback() are no-op, but the mark poisons the outer level,
                // which will roll back everything instead of committing partial work.
                transaction.markRollbackOnly();
                transaction.rollback();
            }
        } catch (RuntimeException ex) {
            transaction.markRollbackOnly();
            transaction.rollback();
            throw ex;
        } finally { transaction.close(); }
        return valueOf(result);
    }

    public <T> T query(String query, Function<JDBCResultSet, T> function) {
        return readOnly(transaction -> transaction.query(query, function));
    }

    public <T> T query(String query, List<JDBCParameter> parameters, Function<JDBCResultSet, T> function) {
        return readOnly(transaction -> transaction.query(query, parameters, function));
    }

    public boolean execute(String query) {
        return mutating(transaction -> transaction.execute(query));
    }

    public boolean execute(String sql, JDBCParameter... parameters) {
        return mutating(transaction -> transaction.execute(sql, List.of(parameters)));
    }

    public boolean execute(String sql, List<JDBCParameter> parameters) {
        return mutating(transaction -> transaction.execute(sql, parameters));
    }

    /**
     * Executes {@code work} in a REQUIRED propagation transaction: if there is already a transaction running
     * on this thread, the block participates in it (does not commit or return the connection to the pool); otherwise
     * opens a new one and commits at the end, or rolls back if {@code work} returns {@link Result.Failure}.
     */
    public <T> T transaction(Function<JDBCTransaction, Result<T, String>> work) {
        return mutating(work);
    }

    /**
     * Gets the number of currently active connections.
     *
     * @return Number of active connections
     * @see #getAvailableConnections()
     * @see #getTotalConnections()
     */
    public int getActiveConnections() {
        return pool.activeCount();
    }

    /**
     * Gets the number of available connections in the pool.
     *
     * @return Number of available connections
     * @see #getTotalConnections()
     * @see #getActiveConnections()
     */
    public int getAvailableConnections() {
        return pool.availableCount();
    }

    /**
     * Gets the total number of connections (active + available).
     *
     * @return Total number of connections
     * @see #getActiveConnections()
     * @see #getAvailableConnections()
     */
    public int getTotalConnections() {
        return pool.totalCount();
    }

    /**
     * Gets the configuration properties for this data source.
     *
     * @return The JDBC properties
     */
    public JDBCProperties properties() {
        return pool.properties().clone();
    }

    /**
     * Checks if this data source is closed.
     *
     * @return true if closed, false otherwise
     * @see #isOpened()
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Checks if this data source is open.
     *
     * @return true if open, false otherwise
     * @see #isClosed()
     */
    public boolean isOpened() {
        return !closed;
    }

    /**
     * Closes this data source and releases all pooled connections.
     * After calling this method, no more connections can be acquired.
     * Every transaction still open (without commit nor rollback) is reported as WARN before the pool closes.
     */
    public void close() {
        Logger.trace("Closing DataSource '%s'", name);
        if (closed) { return; }

        closed = true;
        warnPendingTransactions();
        pool.close();
    }

    /**
     * Reports as WARN each transaction that remains open at the time of closing: they are works that never
     * decided commit/rollback and whose destiny becomes the implicit rollback from the driver when closing the
     * connection. It serves as a trace for the leak (which thread and which block left it open).
     */
    private void warnPendingTransactions() {
        for (val transaction : live) {
            if (!transaction.isPending()) { continue; }

            transaction.rollback();
            Logger.warn("DataSource '%s' closed with transaction '%s' still open (thread '%s', depth %d): rollback-ed",
                    name, transaction.name(), transaction.ownerThread(), transaction.depth());
        }

        live.clear();
    }

    @Override
    public String toString() {
        return "DataSource{name='%s', active=%d, available=%d, total=%d, closed=%s}"
                .formatted(name, getActiveConnections(), getAvailableConnections(),
                        getTotalConnections(), closed);
    }
}
