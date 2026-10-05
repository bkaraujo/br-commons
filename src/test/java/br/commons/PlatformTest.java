package br.commons;

import br.commons.platform.OS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {

    /** Regression (R4): lazy init without synchronization; an unsupported OS returned null. */
    @Test
    void servicesAreSingletonsOrExplicitlyUnsupported() throws Exception {
        if (Platform.CURRENT == OS.LINUX || Platform.CURRENT == OS.WINDOWS) {
            assertSame(Platform.fileSystem(), Platform.fileSystem());
            assertSame(Platform.network(), Platform.network());

            // Concurrent first calls see one instance.
            var seen = new java.util.concurrent.ConcurrentHashMap<Object, Boolean>();
            var threads = new java.util.ArrayList<Thread>();
            for (int i = 0; i < 8; i++) threads.add(Thread.ofPlatform().start(() -> seen.put(Platform.terminal(), true)));
            for (var thread : threads) thread.join(5_000);
            assertEquals(1, seen.size());
        } else {
            assertThrows(UnsupportedOperationException.class, Platform::fileSystem);
        }
    }
}
