package br.commons.persistence.jdbc;

import br.commons.Logger;
import br.commons.Result;
import br.commons.persistence.jdbc.primitives.JDBCConnection;
import br.commons.persistence.jdbc.primitives.JDBCParameter;
import br.commons.persistence.jdbc.primitives.JDBCResultSet;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@NullMarked
public class JDBCTransaction {

    private final DataSource dataSource;

    private final JDBCConnection connection;

    private final String name;

    /** Thread that opened the transaction — only serves to identify the owner in the orphan transaction warning. */
    private final String ownerThread;

    /**
     * Reentrancy level (REQUIRED propagation): the instance is born at level 1 (the {@code begin()} that
     * created it) and each reentrant {@code begin()} on the same thread increments it. Only the outermost level
     * commits/rolls back and returns the connection to the pool — nested ones merely participate.
     */
    private int depth = 1;

    /** Marked by a nested block that failed: the outer level rolls back instead of committing. */
    private boolean rollbackOnly;

    /**
     * {@code true} after the outermost level has committed or rolled back. While {@code false}
     * the transaction is open and pending decision — this is what {@link DataSource#close()} reports.
     */
    private volatile boolean settled;

    /** Actions to run once the outermost level commits; discarded on rollback. */
    private final List<Runnable> afterCommit = new ArrayList<>();

    JDBCTransaction(DataSource dataSource, String name, JDBCConnection connection) {
        this.dataSource = dataSource;
        this.name = name;
        this.connection = connection;
        this.ownerThread = Thread.currentThread().getName();
    }

    /** Registers another level of reentrancy. Called by {@link DataSource#begin(String, long)}. */
    void enter() {
        depth++;
        Logger.trace("Transaction depth %d", depth);
    }

    /** {@code true} when this is the outermost transaction (the one that actually commits/rolls back). */
    boolean isOutermost() {
        return depth == 1;
    }

    /** Poisons the transaction: the outermost level will roll back even if its own work succeeds. */
    void markRollbackOnly() {
        rollbackOnly = true;
    }

    boolean isRollbackOnly() {
        return rollbackOnly;
    }

    /** {@code true} while the transaction is open without commit or rollback. */
    boolean isPending() {
        return !settled;
    }

    String name() {
        return name;
    }

    String ownerThread() {
        return ownerThread;
    }

    int depth() {
        return depth;
    }

    public <T> Result<T, String> execute(Function<JDBCConnection, Result<T, String>> function) {
        try { return function.apply(connection); }
        finally { close(); }
    }

    public Result<Boolean, String> execute(String sql, JDBCParameter... parameters) {
        return execute(sql, List.of(parameters));
    }

    public Result<Boolean, String> execute(String statement, List<JDBCParameter> parameters) {
        return switch (connection.prepareStatement(statement)) {
            case Result.Failure(var error) -> new Result.Failure<>(error);
            case Result.Success(var pstmt) -> {
                if (pstmt == null) yield new Result.Failure<>("PreparedStatement is null");
                try {
                    for (var i = 0; i < parameters.size(); i++) {
                        val parameter = parameters.get(i);
                        pstmt.setObject(i + 1, parameter.value());
                    }

                    yield pstmt.execute();
                } finally { pstmt.close(); }
            }
        };
    }

    public Result<Boolean, String> execute(String statement) {
        return switch (connection.createStatement()) {
            case Result.Failure(var error) -> new Result.Failure<>(error);
            case Result.Success(var pstmt) -> {
                if (pstmt == null) yield new Result.Failure<>("PreparedStatement is null");
                try { yield pstmt.execute(statement); }
                finally { pstmt.close(); }
            }
        };
    }

    public <T> Result<T, String> query(String query, List<JDBCParameter> parameters, Function<JDBCResultSet, T> function) {
        return switch (connection.prepareStatement(query)) {
            case Result.Failure(var error) -> new Result.Failure<>(error);
            case Result.Success(var pstmt) -> {
                if (pstmt == null) yield new Result.Failure<>("PreparedStatement is null");
                try {
                    for (var i = 0; i < parameters.size(); i++) {
                        val parameter = parameters.get(i);
                        pstmt.setObject(i + 1, parameter.value());
                    }
                    yield switch (pstmt.executeQuery()) {
                        case Result.Failure(var error) -> new Result.Failure<>(error);
                        case Result.Success(var rs) -> {
                            if (rs == null) yield new Result.Failure<>("ResultSet is null");
                            try { yield new Result.Success<>(function.apply(rs)); }
                            finally { rs.close(); }
                        }
                    };
                } finally { pstmt.close(); }
            }
        };
    }

    public <T> Result<T, String> query(String query, Function<JDBCResultSet, T> function) {
        return switch (connection.createStatement()) {
            case Result.Failure(var error) -> new Result.Failure<>(error);
            case Result.Success(var stmt) -> {
                if (stmt == null) yield new Result.Failure<>("Statement is null");

                try {
                    yield switch (stmt.executeQuery(query)) {
                        case Result.Failure(var error) -> new Result.Failure<>(error);
                        case Result.Success(var rs) -> {
                            if (rs == null) yield new Result.Failure<>("ResultSet is null");
                            try { yield new Result.Success<>(function.apply(rs)); }
                            finally { rs.close(); }
                        }
                    };
                } finally { stmt.close(); }
            }
        };
    }

    public Result<Boolean, String> commit() {
        // Nested block does not commit: the outermost level decides (REQUIRED propagation).
        if (!isOutermost()) return Result.success(true);
        if (rollbackOnly) return rollback();

        Logger.trace("Committing transaction");
        return connection.commit().map(c -> {
            settled = true;
            connection.close() ;
            runAfterCommit();
            return true;
        });
    }

    public Result<Boolean, String> rollback() {
        // Nested block does not roll back the connection: only signals (see markRollbackOnly) and leaves to outer.
        if (!isOutermost()) return Result.success(true);

        Logger.trace("Rolling back transaction");
        afterCommit.clear();
        return connection.rollback().map(c -> {
            settled = true;
            connection.close() ;
            return true;
        });
    }

    /**
     * Closes a reentrancy level. Only the last one ({@code depth} reaching zero) returns the connection to the
     * pool and frees the thread's slot — without this, a nested {@code close()} (e.g.: the {@code finally} of
     * {@link #execute(Function)}, used in repository introspection) would close someone else's transaction.
     */
    public void close() {
        if (--depth > 0) {
            Logger.trace("Transaction depth %d", depth);
            return;
        }

        connection.close()
                .ifSuccess(c -> unbind())
                .ifFailure(error -> Logger.error("Error closing JDBC transaction: %s", error));
    }

    /** Registers an action for after the outermost commit (see {@link Transactions#afterCommit}). */
    void afterCommit(Runnable action) {
        afterCommit.add(action);
    }

    /** A failing action is logged and does not undo what was committed, nor stop the other actions. */
    private void runAfterCommit() {
        val actions = List.copyOf(afterCommit);
        afterCommit.clear();
        for (val action : actions) {
            try {
                action.run();
            } catch (RuntimeException e) {
                Logger.error("After-commit action failed: %s", e.toString());
            }
        }
    }

    /** Frees the transaction's slot on this thread so the next begin() creates a new one. */
    private void unbind() {
        dataSource.unregister(this);

        val holder = DataSource.transactions.get(name);
        if (holder != null) {
            holder.remove();
        }
    }

}
