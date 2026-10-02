package br.commons.persistence.jdbc;

import br.commons.Result;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the thread-local transaction model of {@link DataSource}. The begin() returns the
 * current transaction of the thread itself (re-obtained/continued) but never shares it between
 * threads — a race that the old model introduced by capturing a single instance in
 * {@code ThreadLocal.withInitial} and serving it to all threads, leaving the slot to be
 * cleaned up after close.
 */
class DataSourceTransactionConcurrencyTest {

    private static JDBCProperties props(String dbName, int min, int max) {
        val p = new JDBCProperties();
        p.name(dbName);
        p.driver("org.h2.Driver");
        p.url("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        p.username("sa");
        p.password("");
        p.autoCommit(true);
        p.validationQuery("SELECT 1");
        p.minPoolSize(min);
        p.maxPoolSize(max);
        p.connectionTimeout(3000);
        return p;
    }

    private static JDBCTransaction unwrap(Result<JDBCTransaction, String> r) {
        return switch (r) {
            case Result.Success(var tx) -> {
                assertTrue(tx != null, "transação não devia ser nula");
                yield tx;
            }
            case Result.Failure(var err) -> throw new AssertionError("begin falhou: " + err);
        };
    }

    /**
     * In the same thread, begin() re-enters the current transaction; close() is <b>balanced</b>
     * (REQUIRED propagation): only when the last level closes does the connection return to the
     * pool and the next begin() opens a new transaction.
     */
    @Test
    void reentrantBeginContinuesThenFreshAfterClose() {
        val dataSource = new DataSource(props("txreentrant", 1, 4));
        try {
            val first = unwrap(dataSource.begin());
            val continued = unwrap(dataSource.begin());
            assertSame(first, continued, "begin() na mesma thread tem de continuar a transação em curso");

            first.close(); // fecha só o nível aninhado
            assertSame(first, unwrap(dataSource.begin()),
                    "close() de um nível aninhado não pode encerrar a transação em curso");

            first.close(); // fecha o nível reaberto acima
            first.close(); // fecha o nível mais externo → devolve a conexão ao pool
            assertEquals(0, dataSource.getActiveConnections(), "o último close tem de devolver a conexão");

            val afterClose = unwrap(dataSource.begin());
            assertNotSame(first, afterClose, "fechados todos os níveis, o begin() tem de abrir uma transação nova");
            afterClose.close();
        } finally {
            dataSource.close();
        }
    }

    /** Concurrent threads must receive distinct transactions (without connection sharing). */
    @Test
    void concurrentBeginsGetDistinctTransactions() throws InterruptedException {
        val n = 6;
        val dataSource = new DataSource(props("txconcurrent", 1, n));
        try {
            val seen = ConcurrentHashMap.<JDBCTransaction>newKeySet();
            val acquired = new CountDownLatch(n);
            val hold = new CountDownLatch(1);
            val failures = new AtomicInteger(0);

            for (int i = 0; i < n; i++) {
                Thread.ofPlatform().start(() -> {
                    switch (dataSource.begin()) {
                        case Result.Success(var tx) -> {
                            if (tx != null) seen.add(tx);
                            acquired.countDown();
                            try { hold.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            if (tx != null) tx.close();
                        }
                        case Result.Failure(var ignored) -> {
                            failures.incrementAndGet();
                            acquired.countDown();
                        }
                    }
                });
            }

            val allFast = acquired.await(2000, TimeUnit.MILLISECONDS);
            // Each thread keeps its own transaction active; with sharing the Set would collapse to 1.
            val snapshot = Set.copyOf(seen);
            hold.countDown();

            assertEquals(0, failures.get(), "nenhum begin devia falhar");
            assertTrue(allFast, "todas as threads deviam adquirir a sua transação depressa");
            assertEquals(n, snapshot.size(), "cada thread tem de ter a sua própria transação (partilha = corrida)");
        } finally {
            dataSource.close();
        }
    }

    /** Burst of begin/close per thread: no connection leaks or starvation. */
    @Test
    void sustainedTransactionBurstReturnsConnections() throws InterruptedException {
        val threads = 12;
        val iters = 40;
        val dataSource = new DataSource(props("txburst", 2, 8));
        try {
            val done = new CountDownLatch(threads);
            val failures = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                Thread.ofPlatform().start(() -> {
                    for (int i = 0; i < iters; i++) {
                        switch (dataSource.begin()) {
                            case Result.Success(var tx) -> { if (tx != null) tx.close(); }
                            case Result.Failure(var ignored) -> failures.incrementAndGet();
                        }
                    }
                    done.countDown();
                });
            }

            assertTrue(done.await(20, TimeUnit.SECONDS), "a rajada de transações tem de terminar sem starvation");
            assertEquals(0, failures.get(), "nenhum begin devia expirar");
            assertEquals(0, dataSource.getActiveConnections(), "todas as conexões deviam estar devolvidas ao pool");
            assertTrue(dataSource.getTotalConnections() <= dataSource.properties().maxPoolSize(),
                    "total de conexões nunca pode exceder o máximo");
        } finally {
            dataSource.close();
        }
    }
}
