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
                assertTrue(storage.write("data.json", key, ("\"" + key + "\"").getBytes(StandardCharsets.UTF_8)).isSuccess());
            }));
        }
        start.countDown();
        for (val thread : threads) thread.join(10_000);

        for (int i = 0; i < 16; i++) {
            val value = first.read("data.json", "k" + i).get();
            assertTrue(value.isPresent(), "k" + i);
            assertEquals("\"k" + i + "\"", new String(value.get(), StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(dir.resolve("data.json.tmp")), "temporary file must be moved, not left behind");
    }

    /** Regression (S3): {@code ..} or an absolute path escaped the root (path traversal). */
    @Test
    void namesOutsideTheRootAreRefused() throws Exception {
        val root = Files.createDirectories(dir.resolve("root"));
        Files.writeString(dir.resolve("secret.json"), "{\"k\":\"secret\"}");
        val storage = new LocalFileStorage(root.toString(), JsonMapper.builder().build());

        assertTrue(storage.read("../secret.json", "k").isFailure());
        assertTrue(storage.read(dir.resolve("secret.json").toString(), "k").isFailure());
        assertTrue(storage.write("../evil.json", "k", "1".getBytes(StandardCharsets.UTF_8)).isFailure());
        assertFalse(Files.exists(dir.resolve("evil.json")));
        assertFalse(storage.exists("../secret.json"));

        assertTrue(storage.write("sub/../ok.json", "k", "1".getBytes(StandardCharsets.UTF_8)).isSuccess());
        assertTrue(storage.read("ok.json", "k").get().isPresent());
    }
}
