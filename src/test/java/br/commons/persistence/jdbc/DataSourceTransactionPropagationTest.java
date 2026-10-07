package br.commons.persistence.jdbc;

import br.commons.Result;
import br.commons.persistence.jdbc.primitives.JDBCResultSet;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transaction propagation (REQUIRED): a nested block participates in the current transaction of the
 * thread — it does not commit, does not rollback, and does not return the connection to the pool.
 * Before this semantics, the first nested operation (a read, a {@code save} from repository, or
 * the introspection of a {@code JDBCRepository}) would close the outer transaction's connection
 * and disconnect the thread slot, breaking a {@code transaction(...)} block into N autonomous
 * transactions — see {@code f999} (user creation).
 */
class DataSourceTransactionPropagationTest {

    private static JDBCProperties props(String dbName) {
        val p = new JDBCProperties();
        p.name(dbName);
        p.driver("org.h2.Driver");
        p.url("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        p.username("sa");
        p.password("");
        p.autoCommit(false);
        p.validationQuery("SELECT 1");
        p.minPoolSize(1);
        p.maxPoolSize(4);
        p.connectionTimeout(3000);
        return p;
    }

    private static DataSource dataSource(String dbName) {
        val dataSource = new DataSource(props(dbName));
        dataSource.execute("CREATE TABLE T (ID BIGINT PRIMARY KEY)");
        return dataSource;
    }

    private static long count(DataSource dataSource) {
        return dataSource.query("SELECT COUNT(*) FROM T", rs -> {
            rs.next().get();
            return rs.getLong(1).get();
        });
    }

    /** Count from another thread — thus, from another connection: only sees what has already been committed. */
    private static long countFromAnotherConnection(DataSource dataSource) {
        val seen = new AtomicLong(-1);
        val thread = Thread.ofPlatform().start(() -> seen.set(count(dataSource)));
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("leitura noutra conexão interrompida");
        }
        assertFalse(thread.isAlive(), "a leitura noutra conexão não devia bloquear");
        return seen.get();
    }

    /** Nested write only becomes visible to another connection when the outer transaction commits. */
    @Test
    void nestedWriteCommitsOnlyWithTheOutermostTransaction() {
        val dataSource = dataSource("txpropwrite");
        try {
            dataSource.transaction(_ -> {
                dataSource.execute("INSERT INTO T VALUES (1)"); // mutating aninhado: participa, não commita
                assertEquals(0, countFromAnotherConnection(dataSource),
                        "escrita aninhada não pode ser commitada antes do fim da transação externa");
                return Result.success(true);
            });

            assertEquals(1, countFromAnotherConnection(dataSource),
                    "o commit do nível mais externo tem de publicar o trabalho aninhado");
        } finally {
            dataSource.close();
        }
    }

    /** Nested read cannot rollback or return the outer transaction's connection to the pool. */
    @Test
    void nestedReadKeepsTheOutermostConnection() {
        val dataSource = dataSource("txpropread");
        try {
            dataSource.transaction(_ -> {
                dataSource.execute("INSERT INTO T VALUES (1)");
                assertEquals(1, dataSource.getActiveConnections());

                assertEquals(1, count(dataSource), "a leitura aninhada tem de enxergar o trabalho em curso");
                assertEquals(1, dataSource.getActiveConnections(),
                        "leitura aninhada não pode devolver a conexão ao pool");

                return Result.success(true);
            });

            assertEquals(0, dataSource.getActiveConnections(), "no fim a conexão tem de voltar ao pool");
            assertEquals(1, countFromAnotherConnection(dataSource));
        } finally {
            dataSource.close();
        }
    }

    /**
     * Building a repository inside a transaction triggers {@code introspect()}
     * ({@code begin()} + {@code execute(Function)}, which closes in {@code finally}) — cannot end
     * the current transaction. Real scenario: {@code UserCategoryJDBCRepository} is created lazy
     * inside the seed listener, already inside the user creation transaction.
     */
    @Test
    void repositoryIntrospectionInsideTransactionDoesNotCloseIt() {
        val dataSource = dataSource("txpropintrospect");
        Context.set(DataSource.class, () -> dataSource);
        try {
            dataSource.transaction(_ -> {
                dataSource.execute("INSERT INTO T VALUES (1)");

                val repository = new TRepository(); // introspect() acontece aqui
                assertFalse(repository.columns().isEmpty());
                assertEquals(1, dataSource.getActiveConnections(),
                        "a introspecção não pode devolver a conexão da transação em curso");

                assertEquals(0, countFromAnotherConnection(dataSource),
                        "a introspecção não pode ter commitado a transação em curso");
                return Result.success(true);
            });

            assertEquals(1, countFromAnotherConnection(dataSource));
        } finally {
            Context.remove(DataSource.class);
            dataSource.close();
        }
    }

    /**
     * {@code markRollbackOnly()} poisons the transaction: the {@code commit()} of the outermost level
     * reverts. Tested directly in {@link JDBCTransaction} because the equivalent path through
     * {@code DataSource} goes through {@code Result.get()} on a failure, which is fatal and shuts
     * down the JVM.
     */
    @Test
    void rollbackOnlyMakesTheOutermostCommitRevert() {
        val dataSource = dataSource("txproppoison");
        try {
            val transaction = switch (dataSource.begin()) {
                case Result.Success(var tx) -> {
                    assertNotNull(tx);
                    yield tx;
                }
                case Result.Failure(var error) -> throw new AssertionError("begin falhou: " + error);
            };

            transaction.execute("INSERT INTO T VALUES (1)").get();
            transaction.markRollbackOnly();
            transaction.commit();
            transaction.close();

            assertEquals(0, count(dataSource), "commit de transação envenenada tem de reverter");
            assertEquals(0, dataSource.getActiveConnections());
        } finally {
            dataSource.close();
        }
    }

    /** Minimal repository on table T — only exists to exercise the {@code introspect()}. */
    private static final class TRepository extends JDBCRepository<Long> {

        TRepository() {
            super("T");
        }

        @Override
        protected Long map(JDBCResultSet rs) {
            return rs.getLong("ID").get();
        }

        @Override
        protected Map<String, @Nullable Object> values(Long entity) {
            val values = new LinkedHashMap<String, @Nullable Object>();
            values.put("ID", entity);
            return values;
        }
    }
}
