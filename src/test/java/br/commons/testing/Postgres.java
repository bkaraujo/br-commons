package br.commons.testing;

import br.commons.Strings;
import lombok.val;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/**
 * One PostgreSQL 18 container per test JVM (Testcontainers; Docker or Podman, spec §2.2), shared by the
 * commons, backend and frontend tests through the commons test-jar. Each test gets its own database.
 *
 * <p>With rootless Podman: {@code DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock}.
 */
public final class Postgres {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:18");

    private Postgres() {}

    private static synchronized PostgreSQLContainer container() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER;
    }

    public static String username() {
        return container().getUsername();
    }

    public static String password() {
        return container().getPassword();
    }

    /** JDBC URL of a new, empty database. */
    public static String newDatabase() {
        val name = "test_" + Strings.lower(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        try (var connection = admin(); var statement = connection.createStatement()) {
            statement.execute("create database " + name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return container().getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    public static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, username(), password());
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(container().getJdbcUrl(), username(), password());
    }
}
