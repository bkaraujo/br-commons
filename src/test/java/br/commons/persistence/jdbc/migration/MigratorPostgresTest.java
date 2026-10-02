package br.commons.persistence.jdbc.migration;

import br.commons.testing.Postgres;
import java.sql.Connection;
import java.sql.SQLException;

class MigratorPostgresTest extends MigratorContract {

    @Override
    String newDatabase() {
        return Postgres.newDatabase();
    }

    @Override
    Connection connect(String url) throws SQLException {
        return Postgres.connect(url);
    }
}
