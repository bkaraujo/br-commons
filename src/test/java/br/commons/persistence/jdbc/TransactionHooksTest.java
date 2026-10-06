package br.commons.persistence.jdbc;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Additions to the POC data source: initializer (RLS binding), inTransaction and afterCommit. */
class TransactionHooksTest {

    private final AtomicInteger initialized = new AtomicInteger();
    private final List<String> events = new ArrayList<>();

    private static JDBCProperties props(String db) {
        val p = new JDBCProperties();
        p.name(db);
        p.driver("org.h2.Driver");
        p.url("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1");
        p.username("sa");
        p.password("");
        p.autoCommit(false);
        p.validationQuery("SELECT 1");
        p.minPoolSize(1);
        p.maxPoolSize(2);
        p.connectionTimeout(3000);
        return p;
    }

    private DataSource dataSource(String db) {
        val dataSource = new DataSource(props(db), transaction -> {
            initialized.incrementAndGet();
            return transaction.query("SELECT 1", rs -> true).map(ignored -> null);
        });
        dataSource.execute("CREATE TABLE T (ID BIGINT PRIMARY KEY)");
        initialized.set(0);
        return dataSource;
    }

    private static long count(DataSource dataSource) {
        return dataSource.query("SELECT COUNT(*) FROM T", rs -> {
            rs.next().get();
            return rs.getLong(1).get();
        });
    }

    @Test
    void initializerRunsOncePerOutermostTransaction() {
        val dataSource = dataSource("hooks_init");
        try {
            dataSource.inTransaction(() -> {
                dataSource.execute("INSERT INTO T VALUES (1)");
                dataSource.execute("INSERT INTO T VALUES (2)");
                return Result.success(true);
            });
            assertEquals(1, initialized.get());
            assertEquals(2, count(dataSource));
        } finally {
            dataSource.close();
        }
    }

    @Test
    void failingInitializerPreventsTheWork() {
        val dataSource = new DataSource(props("hooks_fail"), transaction -> Result.failure("no household"));
        try {
            val error = assertThrows(IllegalStateException.class, () -> dataSource.inTransaction(() -> Result.success(1)));
            assertEquals(Errors.get().transactionInitializerFailed("no household").value(), error.getMessage());
            assertEquals(0, dataSource.getActiveConnections());
        } finally {
            dataSource.close();
        }
    }

    @Test
    void failureAndExceptionRollBack() {
        val dataSource = dataSource("hooks_rollback");
        try {
            val result = dataSource.inTransaction(() -> {
                dataSource.execute("INSERT INTO T VALUES (1)");
                return Result.failure("rule broken");
            });
            assertEquals(Result.failure("rule broken"), result);
            assertThrows(IllegalArgumentException.class, () -> dataSource.inTransaction(() -> {
                dataSource.execute("INSERT INTO T VALUES (2)");
                throw new IllegalArgumentException("boom");
            }));
            assertEquals(0, count(dataSource));
        } finally {
            dataSource.close();
        }
    }

    @Test
    void afterCommitRunsOnlyAfterTheOutermostCommit() {
        val dataSource = dataSource("hooks_after");
        try {
            dataSource.inTransaction(() -> {
                dataSource.afterCommit(() -> events.add("committed"));
                dataSource.afterCommit(() -> { throw new IllegalStateException("listener failure"); });
                dataSource.afterCommit(() -> events.add("still runs"));
                events.add("work");
                return Result.success(true);
            });
            dataSource.inTransaction(() -> {
                dataSource.afterCommit(() -> events.add("discarded"));
                return Result.failure("rolled back");
            });
            dataSource.afterCommit(() -> events.add("no transaction"));
            assertEquals(List.of("work", "committed", "still runs", "no transaction"), events);
        } finally {
            dataSource.close();
        }
    }

    /** Regression (C2): afterCommit only looked at the "default" transaction and ran at once inside a named one. */
    @Test
    void afterCommitWaitsForANamedTransaction() {
        val dataSource = dataSource("hooks_named");
        try {
            val transaction = dataSource.begin("other").get();
            dataSource.afterCommit(() -> events.add("after other"));
            events.add("work");
            assertEquals(List.of("work"), events);
            transaction.commit();
            transaction.close();
            assertEquals(List.of("work", "after other"), events);
        } finally {
            dataSource.close();
        }
    }

    /** Regression (C1): an exception from the work became RuntimeException("Unreachable"), losing type and cause. */
    @Test
    void exceptionsReachTheCallerUnchanged() {
        val dataSource = dataSource("hooks_exception");
        try {
            val error = new br.commons.business.BusinessException(new br.commons.business.BusinessError.Conflict("dup"));
            val thrown = assertThrows(br.commons.business.BusinessException.class, () -> dataSource.transaction(tx -> {
                tx.execute("INSERT INTO T VALUES (1)");
                throw error;
            }));
            assertEquals(error, thrown);
            assertEquals(0, count(dataSource), "rolled back");

            val sqlError = assertThrows(IllegalStateException.class, () -> dataSource.execute("INSERT INTO NOPE VALUES (1)"));
            org.junit.jupiter.api.Assertions.assertTrue(sqlError.getMessage().contains("NOPE"), sqlError.getMessage());
        } finally {
            dataSource.close();
        }
    }
}
