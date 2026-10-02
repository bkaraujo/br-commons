package br.commons.persistence.jdbc.pool;

import br.commons.Result;
import br.commons.persistence.jdbc.JDBCProperties;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces pool exhaustion under concurrency (symptom: "Connection timeout: no connection
 * available within ...ms"). The application path always returns connections; the defect is in
 * the pool itself when multiple requests compete — a scenario never exercised by MockMvc tests
 * (sequential) nor by in-memory repositories.
 */
class ConnectionPoolConcurrencyTest {

    private static JDBCProperties props(String dbName, int min, int max, int timeoutMs) {
        val p = new JDBCProperties();
        p.driver("org.h2.Driver");
        p.url("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        p.username("sa");
        p.password("");
        p.autoCommit(true);
        p.validationQuery("SELECT 1");
        p.minPoolSize(min);
        p.maxPoolSize(max);
        p.connectionTimeout(timeoutMs);
        return p;
    }

    /**
     * With N concurrent requests (N &le; max) the pool must grow and serve them all quickly.
     * The buggy pool blocks each waiter for the entire connectionTimeout before creating a new
     * connection, so N-1 of them sit idle ~timeout instead of acquiring immediately.
     */
    @Test
    void growsToMeetConcurrentDemand() throws InterruptedException {
        val pool = new ConnectionPool(props("poolgrow", 1, 6, 3000));
        try {
            val n = 6;
            val acquired = new CountDownLatch(n);
            val hold = new CountDownLatch(1);
            val failures = new AtomicInteger(0);

            for (int i = 0; i < n; i++) {
                Thread.ofPlatform().start(() -> {
                    switch (pool.aquire()) {
                        case Result.Success(var conn) -> {
                            acquired.countDown();
                            try { hold.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            conn.close();
                        }
                        case Result.Failure(var ignored) -> {
                            failures.incrementAndGet();
                            acquired.countDown();
                        }
                    }
                });
            }

            val allFast = acquired.await(1000, TimeUnit.MILLISECONDS);
            hold.countDown();

            assertEquals(0, failures.get(), "nenhum pedido devia falhar ao adquirir");
            assertTrue(allFast, "pool tem de crescer para servir " + n + " pedidos concorrentes depressa (cresceu tarde demais?)");
        } finally {
            pool.close();
        }
    }

    /**
     * Sustained burst cannot lose connections or exceed the maximum: after all work is done the
     * accounting must be consistent (without offer() silently discarded, without total above the
     * maximum due to race in check-then-create).
     */
    @Test
    void sustainedBurstKeepsAccountingConsistent() throws InterruptedException {
        val pool = new ConnectionPool(props("poolburst", 2, 8, 3000));
        try {
            val threads = 16;
            val iters = 60;
            val done = new CountDownLatch(threads);
            val failures = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                Thread.ofPlatform().start(() -> {
                    for (int i = 0; i < iters; i++) {
                        switch (pool.aquire()) {
                            case Result.Success(var conn) -> conn.close();
                            case Result.Failure(var ignored) -> failures.incrementAndGet();
                        }
                    }
                    done.countDown();
                });
            }

            assertTrue(done.await(20, TimeUnit.SECONDS), "a rajada tem de terminar sem starvation");
            assertEquals(0, failures.get(), "nenhuma aquisição devia expirar");
            assertTrue(pool.totalCount() <= pool.properties().maxPoolSize(), "total de conexões nunca pode exceder o máximo (corrida no create?)");
            assertEquals(0, pool.activeCount(), "todas as conexões deviam estar devolvidas");
            assertEquals(pool.totalCount(), pool.availableCount(), "conexões devolvidas têm de voltar ao pool (offer descartado em silêncio?)");
        } finally {
            pool.close();
        }
    }
}
