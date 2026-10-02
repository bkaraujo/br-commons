package br.commons.persistence.jdbc.migration;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes migrations of the same database. PostgreSQL: a session advisory lock, which also covers two
 * instances starting on different machines. H2: a lock in this JVM per database URL, because H2 has no
 * advisory lock and is used embedded (tests, development), where only one JVM opens the database.
 */
@NullMarked
sealed interface MigrationLock permits MigrationLock.Advisory, MigrationLock.InJvm {

    void acquire(Connection connection) throws SQLException;

    void release(Connection connection) throws SQLException;

    /** The lock for the connection's database, or empty when the database is not supported. */
    static Optional<MigrationLock> of(Connection connection) throws SQLException {
        val metadata = connection.getMetaData();
        return switch (metadata.getDatabaseProductName().toUpperCase(Locale.ROOT)) {
            case "POSTGRESQL" -> Optional.of(new Advisory());
            case "H2" -> Optional.of(new InJvm(metadata.getURL()));
            default -> Optional.empty();
        };
    }

    /** PostgreSQL {@code pg_advisory_lock}, held by the migrator's session until released. */
    @NullMarked
    final class Advisory implements MigrationLock {

        /** Arbitrary key shared by every JUDAS instance. */
        private static final long KEY = 7_318_540_223L;

        @Override
        public void acquire(Connection connection) throws SQLException {
            execute(connection, "select pg_advisory_lock(" + KEY + ")");
        }

        @Override
        public void release(Connection connection) throws SQLException {
            execute(connection, "select pg_advisory_unlock(" + KEY + ")");
        }

        private static void execute(Connection connection, String sql) throws SQLException {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
            connection.commit();
        }
    }

    /** One {@link ReentrantLock} per database URL in this JVM. */
    @NullMarked
    final class InJvm implements MigrationLock {

        private static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

        private final ReentrantLock lock;

        InJvm(String url) {
            this.lock = LOCKS.computeIfAbsent(url, key -> new ReentrantLock());
        }

        @Override
        public void acquire(Connection connection) {
            lock.lock();
        }

        @Override
        public void release(Connection connection) {
            lock.unlock();
        }
    }
}
