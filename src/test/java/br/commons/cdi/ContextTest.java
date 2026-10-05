package br.commons.cdi;

import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ContextTest {

    static final class Ready {}
    static final class Slow {}
    static final class Other {}

    @AfterEach
    void clear() {
        Context.clear();
    }

    /** Regression (R9): {@code set} ran a slow supplier inside the lock and blocked every other operation. */
    @Test
    void slowSupplierInSetDoesNotBlockOthers() throws Exception {
        Context.set(Ready.class, Ready::new);
        val entered = new CountDownLatch(1);
        val release = new CountDownLatch(1);
        val setter = Thread.ofVirtual().start(() -> Context.set(Slow.class, () -> {
            entered.countDown();
            await(release);
            return new Slow();
        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertNotNull(Context.get(Ready.class));
            assertNotNull(Context.tryGet(Other.class, Other::new));
        });

        release.countDown();
        setter.join();
        assertNotNull(Context.get(Slow.class));
    }

    /** Regression (R9): {@code get} took the lock, so a {@code tryGet} creating something blocked every read. */
    @Test
    void getDoesNotWaitForTheLock() throws Exception {
        Context.set(Ready.class, Ready::new);
        val entered = new CountDownLatch(1);
        val release = new CountDownLatch(1);
        val creator = Thread.ofVirtual().start(() -> Context.tryGet(Slow.class, () -> {
            entered.countDown();
            await(release);
            return new Slow();
        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertNotNull(Context.get(Ready.class)));

        release.countDown();
        creator.join();
    }

    @Test
    void getWithoutRegistrationThrows() {
        assertThrows(IllegalStateException.class, () -> Context.get(Other.class));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
