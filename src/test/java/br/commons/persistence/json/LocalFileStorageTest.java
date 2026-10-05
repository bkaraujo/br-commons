package br.commons.persistence.json;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {

    @TempDir
    Path dir;

    /** Regression (C5): concurrent read-modify-write of the same file lost fields. */
    @Test
    void concurrentWritesToTheSameFileKeepEveryKey() throws Exception {
        val mapper = JsonMapper.builder().build();
        val first = new LocalFileStorage(dir.toString(), mapper);
        val second = new LocalFileStorage(dir.toString(), mapper); // locks are shared across instances
        val start = new CountDownLatch(1);
        val threads = new ArrayList<Thread>();
        for (int i = 0; i < 16; i++) {
            val key = "k" + i;
            val storage = i % 2 == 0 ? first : second;
            threads.add(Thread.ofPlatform().start(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                storage.write("data.json", key, ("\"" + key + "\"").getBytes(StandardCharsets.UTF_8));
            }));
        }
        start.countDown();
        for (val thread : threads) thread.join(10_000);

        for (int i = 0; i < 16; i++) {
            val value = first.read("data.json", "k" + i);
            assertNotNull(value, "k" + i);
            assertEquals("\"k" + i + "\"", new String(value, StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(dir.resolve("data.json.tmp")), "temporary file must be moved, not left behind");
    }
}
