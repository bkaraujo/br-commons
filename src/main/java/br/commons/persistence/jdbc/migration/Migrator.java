package br.commons.persistence.jdbc.migration;

import br.commons.Logger;
import br.commons.Result;
import br.commons.locale.i18n.Errors;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The minimum of what Flyway gave (spec §3.5): {@code V<n>__<description>.sql} files applied in ascending
 * order, each in its own transaction; a control table {@code schema_migration}; a lock so that two starts
 * do not apply the same migration twice ({@link MigrationLock}); and the SHA-256 of every applied file
 * checked at each start, so that an applied migration edited afterwards stops the start. Forward only: a
 * mistake is fixed by another migration, and going back is a restore (spec §15.3).
 *
 * <p>Supports PostgreSQL and H2. In H2, DDL commits implicitly, so a failed migration keeps the DDL that ran
 * before the failure (its DML and its {@code schema_migration} row are rolled back, as in PostgreSQL).
 */
@NullMarked
public final class Migrator {

    private static final String CONTROL_TABLE = """
            create table if not exists schema_migration (
              version     int primary key,
              description varchar(200) not null,
              checksum    char(64) not null,
              applied_at  timestamp with time zone default current_timestamp not null
            )""";

    private final ConnectionFactory connections;
    private final MigrationScanner scanner;

    public Migrator(ConnectionFactory connections, String location) {
        this(connections, location, Migrator.class.getClassLoader());
    }

    public Migrator(ConnectionFactory connections, String location, ClassLoader loader) {
        this.connections = connections;
        this.scanner = new MigrationScanner(loader, location);
    }

    /** Applies the pending migrations and returns them. */
    public Result<List<Migration>, String> migrate() {
        return scanner.scan().flatMap(this::migrate);
    }

    /** Migrations found but not applied yet (readiness check, spec §14.4). */
    public Result<List<Migration>, String> pending() {
        return scanner.scan().flatMap(found -> withConnection(connection -> {
            ensureControlTable(connection);
            return verify(found, applied(connection));
        }));
    }

    private Result<List<Migration>, String> migrate(List<Migration> found) {
        return withConnection(connection -> {
            val lock = MigrationLock.of(connection);
            if (lock.isEmpty()) {
                return Result.failure(Errors.get().unsupportedDatabase(connection.getMetaData().getDatabaseProductName()).value());
            }
            lock.get().acquire(connection);
            try {
                ensureControlTable(connection);
                return switch (verify(found, applied(connection))) {
                    case Result.Success<List<Migration>, String>(var pending) -> apply(connection, pending);
                    case Result.Failure<List<Migration>, String> failure -> failure;
                };
            } finally {
                lock.get().release(connection);
            }
        });
    }

    private Result<List<Migration>, String> apply(Connection connection, List<Migration> pending) throws SQLException {
        val applied = new ArrayList<Migration>();
        for (val migration : pending) {
            try (var statement = connection.createStatement();
                    var insert = connection.prepareStatement(
                            "insert into schema_migration (version, description, checksum) values (?, ?, ?)")) {
                statement.execute(migration.sql());
                insert.setInt(1, migration.version());
                insert.setString(2, migration.description());
                insert.setString(3, migration.checksum());
                insert.executeUpdate();
                connection.commit();
                applied.add(migration);
                Logger.info("Applied migration V%d (%s)", migration.version(), migration.description());
            } catch (SQLException e) {
                connection.rollback();
                return Result.failure(Errors.get().migrationFailed(String.valueOf(migration.version()), String.valueOf(e.getMessage())).value());
            }
        }
        return Result.success(applied);
    }

    /** Pending migrations, after checking that every applied one still exists unchanged. */
    private static Result<List<Migration>, String> verify(List<Migration> found, Map<Integer, String> applied) {
        val byVersion = new HashMap<Integer, Migration>();
        found.forEach(migration -> byVersion.put(migration.version(), migration));
        for (val entry : applied.entrySet()) {
            val file = byVersion.get(entry.getKey());
            if (file == null) {
                return Result.failure(Errors.get().appliedMigrationMissing(String.valueOf(entry.getKey())).value());
            }
            if (!file.checksum().equals(entry.getValue())) {
                return Result.failure(Errors.get().appliedMigrationChanged(String.valueOf(entry.getKey())).value());
            }
        }
        return Result.success(found.stream().filter(migration -> !applied.containsKey(migration.version())).toList());
    }

    private static Map<Integer, String> applied(Connection connection) throws SQLException {
        val applied = new HashMap<Integer, String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("select version, checksum from schema_migration")) {
            while (rows.next()) {
                applied.put(rows.getInt(1), rows.getString(2));
            }
        }
        connection.commit();
        return applied;
    }

    private static void ensureControlTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(CONTROL_TABLE);
        }
        connection.commit();
    }

    private Result<List<Migration>, String> withConnection(Work work) {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            return work.run(connection);
        } catch (SQLException e) {
            return Result.failure(Errors.get().migrationRunFailed(String.valueOf(e.getMessage())).value());
        }
    }

    @FunctionalInterface
    @NullMarked
    private interface Work {
        Result<List<Migration>, String> run(Connection connection) throws SQLException;
    }
}
