package br.commons.persistence.jdbc;

import br.commons.Result;
import br.commons.persistence.jdbc.primitives.JDBCParameter;
import org.jspecify.annotations.NullMarked;

/**
 * Hook run when the outermost transaction of a thread opens, before any work (spec §4.3). The JUDAS one
 * binds the household for Row-Level Security with {@code set_config(..., true)}, which lasts only for the
 * transaction and is therefore safe with a connection pool. Use {@link JDBCTransaction#query} or
 * {@link JDBCTransaction#execute(String, JDBCParameter...)}; never {@link JDBCTransaction#execute(java.util.function.Function)},
 * which closes the transaction.
 */
@NullMarked
@FunctionalInterface
public interface TransactionInitializer {

    TransactionInitializer NONE = transaction -> Result.success();

    Result<Void, String> initialize(JDBCTransaction transaction);
}
