package br.commons.persistence.jdbc.pool;

import br.commons.*;
import br.commons.persistence.jdbc.JDBCProperties;
import br.commons.persistence.jdbc.Results;
import br.commons.persistence.jdbc.primitives.JDBCConnection;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@NullMarked
public final class ConnectionPool {
    private final JDBCProperties properties;
    private final BlockingQueue<PooledConnection> availableConnections;
    private final ConcurrentHashMap<Connection, PooledConnection> activeConnections;
    private final AtomicInteger totalConnections;
    private volatile boolean closed;

    public ConnectionPool(JDBCProperties p) {
        this.properties = p;

        closed = false;
        totalConnections = new AtomicInteger(0);
        activeConnections = new ConcurrentHashMap<>();
        // Unlimited: there can never be more returned connections than the total created (<= max),
        // and a silently refused offer() would lose the connection without decrementing the total (starve).
        availableConnections = new LinkedBlockingQueue<>();

        // Initialize minimum pool size
        try {
            for (int i = 0; i < properties.minPoolSize(); i++) {
                availableConnections.offer(createConnection());
            }
        } catch (SQLException ex ){
            Logger.error("Failed to initialize connection pool '%s': %s", properties.name(), ex.toString());
            throw new RuntimeException("Failed to initialize connection pool: " + ex.toString());
        }

        Threads.virtual(() -> {
            while (RT.running) {
                Threads.sleep(30_000);

                if (closed) return;
                performMaintenance();
            }
        });

        Logger.info("Connection pool '%s' initialized (min=%d, max=%d)", properties.name(), properties.minPoolSize(), properties.maxPoolSize());
        Logger.trace("%s", Logger.lazy(() -> Objects.toString(properties)));
    }

    /** Creates a connection and accounts it in the total. Used by initialize()/maintenance. */
    private PooledConnection createConnection() throws SQLException {
        val pooled = openConnection();
        totalConnections.incrementAndGet();
        Logger.trace("Created new connection for pool '%s' (total=%d)", properties.name(), totalConnections.get());

        return pooled;
    }

    /** Opens a raw connection without touching the counter (the caller has already reserved the slot). */
    private PooledConnection openConnection() throws SQLException {
        if (closed) {
            throw new SQLException("Connection pool is closed");
        }

        try {
            // Load JDBC driver
            Class.forName(properties.driver());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + properties.driver(), e);
        }

        val rawConnection = DriverManager.getConnection(
                properties.url(),
                properties.username(),
                properties.password()
        );

        rawConnection.setAutoCommit(properties.autoCommit());

        return new PooledConnection(rawConnection);
    }

    public Result<JDBCConnection, String> aquire() {
        return aquire(properties.connectionTimeout());
    }

    public Result<JDBCConnection, String> aquire(long timeoutMs) {
        if (closed) {
            return Results.resourceIsClosed(properties.name());
        }

        try {
            val pooled = obtain(timeoutMs);
            if (pooled == null) {
                return Results.poolExhausted(properties.name(), timeoutMs);
            }

            // Validate connection
            if (!isValid(pooled)) {
                closeConnection(pooled);
                return aquire(timeoutMs);
            }

            // Update last access time and mark as active
            pooled.updateLastAccess();
            activeConnections.put(pooled.connection(), pooled);

            Logger.trace("Connection acquired from pool '%s' (active=%d, available=%d)",
                    properties.name(), activeConnections.size(), availableConnections.size());

            return Result.success(new JDBCConnection(new ConnectionWrapper(pooled.connection(), this)));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Results.error(properties.name(), e);
        } catch (SQLException e) {
            return Results.error(properties.name(), e);
        }
    }

    /**
     * Takes a connection from the pool. First tries an idle connection immediately; if none exists and
     * there is still room (&lt; max), creates a new one immediately — without blocking by timeout. Only when the
     * pool is at maximum does it wait (up to {@code timeoutMs}) for a return.
     */
    @Nullable
    private PooledConnection obtain(long timeoutMs) throws InterruptedException, SQLException {
        // 1) Fast path: an idle connection, if it exists.
        val pooled = availableConnections.poll();
        if (pooled != null) return pooled;

        // 2) Grows immediately if there is room — reserves the slot via CAS (does not exceed the maximum).
        while (true) {
            val current = totalConnections.get();
            if (current >= properties.maxPoolSize()) break;
            if (totalConnections.compareAndSet(current, current + 1)) {
                try {
                    return openConnection();
                } catch (SQLException ex) {
                    totalConnections.decrementAndGet(); // undoes the reservation if creation fails
                    throw ex;
                }
            }
        }

        // 3) At maximum: wait for a returned connection.
        return availableConnections.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    void release(Connection connection) {
        val pooled = activeConnections.remove(connection);
        if (pooled == null) {
            Logger.warn("Attempted to release unknown connection to pool '%s'", properties.name());
            return;
        }

        try {
            // Reset connection state
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(properties.autoCommit());
            }

            pooled.updateLastAccess();
            if (!availableConnections.offer(pooled)) {
                closeConnection(pooled); // queue full (should not occur): closes and decrements, never loses the count
            }

            Logger.trace("Connection released to pool '%s' (active=%d, available=%d)",
                    properties.name(), activeConnections.size(), availableConnections.size());

        } catch (SQLException e) {
            Logger.warn("Error releasing connection to pool '%s': %s", properties.name(), Strings.orEmpty(e.getMessage()));
            closeConnection(pooled);
        }
    }

    private boolean isValid(PooledConnection pooled) {
        try {
            // Check if connection exceeded max lifetime
            if (System.currentTimeMillis() - pooled.createdAt() > properties.maxLifetime()) {
                Logger.trace("Connection exceeded max lifetime in pool '%s'", properties.name());
                return false;
            }

            // Use validation query if provided
            if (properties.validationQuery() != null) {
                // Do not close the pool connection: only the Statement/ResultSet. A try-with-resources
                // over pooled.connection() would close the physical connection at each validation, leaving the pool
                // with dead connections (but still counted) → churn and starvation under concurrency.
                val connection = pooled.connection();
                try (val stmt = connection.createStatement()) {
                    stmt.executeQuery(properties.validationQuery()).close();
                }
                return true;
            }

            // Fallback to isValid method
            return pooled.connection().isValid(1);

        } catch (SQLException e) {
            Logger.trace("Connection validation failed in pool '%s': %s", properties.name(), Strings.orEmpty(e.getMessage()));
            return false;
        }
    }

    private void performMaintenance() {
        try {
            Logger.trace("Performing maintenance on pool '%s'", properties.name());

            val removed = removeIdleConnections();
            if (removed > 0) {
                Logger.trace("Removed %d idle connections from pool '%s'", removed, properties.name());
            }

            ensureMinimumPoolSize();

        } catch (Exception e) {
            Logger.error("Error during pool maintenance for '%s': %s", properties.name(), Strings.orEmpty(e.getMessage()));
        }
    }

    /** Removes idle connections beyond the idle-timeout, always preserving the minimum pool size. */
    private int removeIdleConnections() {
        val idleDeadline = System.currentTimeMillis() - properties.idleTimeout();
        val iterator = availableConnections.iterator();
        int removed = 0;

        while (iterator.hasNext()) {
            val pooled = iterator.next();

            // Keep minimum pool size
            if (totalConnections.get() <= properties.minPoolSize()) {
                break;
            }

            if (pooled.lastAccessedAt().get() < idleDeadline) {
                iterator.remove();
                closeConnection(pooled);
                removed++;
            }
        }

        return removed;
    }

    /** Recreates connections until reaching the minimum pool size. */
    private void ensureMinimumPoolSize() {
        while (totalConnections.get() < properties.minPoolSize()) {
            try {
                availableConnections.offer(createConnection());
            } catch (SQLException e) {
                Logger.error("Failed to maintain minimum pool size for '%s': %s",
                        properties.name(), Strings.orEmpty(e.getMessage()));
                break;
            }
        }
    }

    private void closeConnection(PooledConnection pooled) {
        try {
            pooled.connection().close();
            totalConnections.decrementAndGet();
            Logger.trace("Closed connection from pool '%s' (total=%d)", properties.name(), totalConnections.get());
        } catch (SQLException e) {
            Logger.warn("Error closing connection in pool '%s': %s", properties.name(), Strings.orEmpty(e.getMessage()));
        }
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        Logger.info("Closing connection pool '%s'", properties.name());

        // Close all active connections (force)
        for (val pooled : activeConnections.values()) {
            Logger.warn("Forcing close of active connection in pool '%s'", properties.name());
            closeConnection(pooled);
        }
        activeConnections.clear();

        // Close all available connections
        PooledConnection pooled;
        while ((pooled = availableConnections.poll()) != null) {
            closeConnection(pooled);
        }

        Logger.info("Connection pool '%s' closed", properties.name());
    }

    public int activeCount() {
        return activeConnections.size();
    }

    public int availableCount() {
        return availableConnections.size();
    }

    public int totalCount() {
        return totalConnections.get();
    }

    public JDBCProperties properties() {
        return properties;
    }

    public boolean isClosed() {
        return closed;
    }

}
