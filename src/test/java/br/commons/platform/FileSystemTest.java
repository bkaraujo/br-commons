package br.commons.platform;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileSystemTest {

    /** Regression: {@code copy} refused every writable target and wrote nothing (inverted check). */
    @Test
    void copyCreatesANewFile(@TempDir Path dir) throws Exception {
        val target = dir.resolve("new.txt");

        FileSystem.copy("olá".getBytes(StandardCharsets.UTF_8), target);

        assertEquals("olá", Files.readString(target));
    }

    @Test
    void copyReplacesAnExistingFile(@TempDir Path dir) throws Exception {
        val target = Files.writeString(dir.resolve("old.txt"), "antigo, mais longo");

        FileSystem.copy("novo".getBytes(StandardCharsets.UTF_8), target);

        assertEquals("novo", Files.readString(target));
    }

    @Test
    void copyIntoAMissingDirectoryWritesNothing(@TempDir Path dir) {
        val target = dir.resolve("missing").resolve("file.txt");

        FileSystem.copy(new byte[] {1}, target);

        assertFalse(Files.exists(target));
    }
}
