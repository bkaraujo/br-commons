package br.commons.persistence.jdbc.migration;

import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens the migrator's own connection, as the schema owner, outside the application pool (spec §3.5, §4.3). */
@FunctionalInterface
@NullMarked
public interface ConnectionFactory {

    Connection open() throws SQLException;
}
