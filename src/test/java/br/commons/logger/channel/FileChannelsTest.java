package br.commons.logger.channel;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FileChannelsTest {

    @TempDir
    Path dir;

    /** Regression (C13): the file was truncated on open and the last messages were lost on shutdown. */
    @Test
    void fileChannelAppendsAndDrainsOnClose() throws Exception {
        val file = dir.resolve("app.log");
        Files.writeString(file, "previous run\n");

        val channel = new FileChannel(file);
        channel.write("last words\n");
        channel.close();

        val content = Files.readString(file);
        assertTrue(content.startsWith("previous run\n"), content);
        assertTrue(content.contains("last words"), content);
    }

    @Test
    void fileChannelCanStillTruncate() throws Exception {
        val file = dir.resolve("fresh.log");
        Files.writeString(file, "previous run\n");

        val channel = new FileChannel(file, FileChannel.Mode.TRUNCATE);
        channel.write("only this\n");
        channel.close();

        assertEquals("only this", Files.readString(file).strip());
    }

    /** Regression (C12): the writer loop depended on RT.running; created before the runtime started, it never wrote. */
    @Test
    void rollingChannelWritesWithoutTheRuntimeAndDrainsOnClose() throws Exception {
        val channel = new RollingFileChannel(dir);
        channel.write("rolled\n");
        channel.close();

        val file = dir.resolve(LocalDate.now(java.time.ZoneOffset.UTC) + ".log");
        assertTrue(Files.readString(file).contains("rolled"));
    }
}
