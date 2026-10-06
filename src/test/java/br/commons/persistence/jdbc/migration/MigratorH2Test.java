package br.commons.persistence.jdbc.migration;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigratorH2Test extends MigratorContract {

    @Override
    String newDatabase() {
        return "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    @Override
    Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    @Test
    void refusesAnUnsupportedDatabase() throws Exception {
        val metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn("Oracle");
        val connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metadata);
        val migrator = new Migrator(() -> connection, "db/migration", MigratorH2Test.class.getClassLoader());
        assertEquals(Result.failure(Errors.get().unsupportedDatabase("Oracle").value()), migrator.migrate());
    }
}
