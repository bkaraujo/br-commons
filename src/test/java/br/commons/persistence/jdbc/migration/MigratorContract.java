package br.commons.persistence.jdbc.migration;

import br.commons.i18n.Errors;

import br.commons.Result;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour every supported database must show; one subclass per database. */
abstract class MigratorContract {

    private static final Map<String, String> OK = Map.of(
            "V1__create_item.sql", "create table item (id int primary key);",
            "V2__first_item.sql", "insert into item values (1);",
            "V10__tenth_item.sql", "insert into item values (10);\ninsert into item values (11);",
            "README.txt", "not a migration");

    @TempDir
    Path root;

    private final String url = newDatabase();

    /** JDBC URL of a new, empty database. */
    abstract String newDatabase();

    abstract Connection connect(String url) throws SQLException;

    Migrator migrator(Path classpathRoot) throws IOException {
        val loader = new URLClassLoader(new URL[] {classpathRoot.toUri().toURL()}, null);
        return new Migrator(() -> connect(url), "db/migration", loader);
    }

    private Path write(Map<String, String> files) throws IOException {
        val dir = Files.createDirectories(root.resolve("db/migration"));
        for (val file : files.entrySet()) {
            Files.writeString(dir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        return root;
    }

    private List<Integer> versions(Result<List<Migration>, String> result) {
        return result.getOrThrow().stream().map(Migration::version).toList();
    }

    private long count(String sql) throws SQLException {
        try (var connection = connect(url); var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void appliesPendingMigrationsInOrderOnlyOnce() throws Exception {
        val migrator = migrator(write(OK));
        assertEquals(List.of(1, 2, 10), versions(migrator.pending()));
        assertEquals(List.of(1, 2, 10), versions(migrator.migrate()));
        assertEquals(List.of(), versions(migrator.migrate()));
        assertEquals(List.of(), versions(migrator.pending()));
        assertEquals(3, count("select count(*) from item"));
        assertEquals(3, count("select count(*) from schema_migration"));
    }

    @Test
    void failedMigrationIsRolledBackAndStopsTheRest() throws Exception {
        val migrator = migrator(write(Map.of(
                "V1__create_item.sql", "create table item (id int primary key);",
                "V2__broken.sql", "insert into item values (2); create tabel oops;",
                "V3__never.sql", "insert into item values (3);")));
        val result = migrator.migrate();
        assertTrue(result instanceof Result.Failure<List<Migration>, String>(var error) && error.startsWith(Errors.get().migrationFailed("2", "").value().split(":")[0]), result::toString);
        assertEquals(0, count("select count(*) from item"));
        assertEquals(1, count("select count(*) from schema_migration"));
    }

    @Test
    void appliedMigrationEditedAfterwardsStopsTheStart() throws Exception {
        val dir = write(OK);
        migrator(dir).migrate().getOrThrow();
        Files.writeString(dir.resolve("db/migration/V2__first_item.sql"), "insert into item values (2);");
        val result = migrator(dir).migrate();
        assertEquals(Result.failure(Errors.get().appliedMigrationChanged("2").value()), result);
    }

    @Test
    void appliedMigrationThatDisappearedStopsTheStart() throws Exception {
        val dir = write(OK);
        migrator(dir).migrate().getOrThrow();
        Files.delete(dir.resolve("db/migration/V10__tenth_item.sql"));
        assertEquals(Result.failure(Errors.get().appliedMigrationMissing("10").value()), migrator(dir).pending());
    }

    @Test
    void duplicateVersionsAreRejected() throws Exception {
        val migrator = migrator(write(Map.of("V1__a.sql", "select 1;", "V1__b.sql", "select 2;")));
        assertEquals(Result.failure(Errors.get().duplicateMigrationVersion("1").value()), migrator.migrate());
    }

    @Test
    void readsMigrationsInsideAJar() throws Exception {
        val jar = root.resolve("migrations.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("db/migration/"));
            for (val file : OK.entrySet()) {
                out.putNextEntry(new JarEntry("db/migration/" + file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        val loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
        val migrator = new Migrator(() -> connect(url), "db/migration/", loader);
        assertEquals(List.of(1, 2, 10), versions(migrator.migrate()));
    }

    @Test
    void concurrentStartsApplyEachMigrationOnce() throws Exception {
        val migrator = migrator(write(OK));
        Callable<List<Integer>> start = () -> versions(migrator.migrate());
        try (var executor = Executors.newFixedThreadPool(2)) {
            val a = executor.submit(start);
            val b = executor.submit(start);
            assertEquals(3, a.get().size() + b.get().size());
        }
        assertEquals(3, count("select count(*) from schema_migration"));
    }

    @Test
    void connectionFailureIsAFailure() throws Exception {
        val loader = new URLClassLoader(new URL[] {write(OK).toUri().toURL()}, null);
        val migrator = new Migrator(() -> { throw new SQLException("refused"); }, "db/migration", loader);
        assertEquals(Result.failure(Errors.get().migrationRunFailed("refused").value()), migrator.migrate());
    }
}
