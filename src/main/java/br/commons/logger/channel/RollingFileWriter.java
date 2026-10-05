package br.commons.logger.channel;

import br.commons.Threads;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static br.commons.chrono.Dates.MILLIS_IN_DAY;

@NullMarked
final class RollingFileWriter implements Runnable {
    private final Path rootDirectory;
    private final Queue<String> messages;

    private @org.jspecify.annotations.Nullable BufferedWriter currentWriter;
    private long currentDayCached;

    /** Own flag, not {@code RT.running}: a channel created before the runtime starts must still write. */
    private volatile boolean running = true;
    private final CountDownLatch finished = new CountDownLatch(1);

    public RollingFileWriter(Queue<String> messages, Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.messages = messages;
    }

    @Override
    public void run() {
        // Initialize the first writer
        rotateWriter();

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                val nowDay = System.currentTimeMillis() / MILLIS_IN_DAY;
                if (nowDay != currentDayCached) { rotateWriter(); }

                if (messages.isEmpty()) {
                    Threads.sleep(100);
                    continue;
                }
                drain();
            }
            drain();
            close(currentWriter);
        } finally {
            finished.countDown();
        }
    }

    /** Stops the loop and waits (up to {@code timeoutMillis}) for the queue to be written and the file closed. */
    void stop(long timeoutMillis) {
        running = false;
        try {
            finished.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void drain() {
        String message;
        val writer = currentWriter;
        if (writer == null) return;
        while ((message = messages.poll()) != null) {
            write(writer, message);
        }
        flush(writer);
    }

    private void rotateWriter() {
        try {
            // Close the previous one if it exists
            val writer = currentWriter;
            if (writer != null) {
                writer.flush();
                writer.close();
            }

            // Update the current day cache
            val nowMillis = System.currentTimeMillis();
            currentDayCached = nowMillis / MILLIS_IN_DAY;

            // Create the new file name: e.g: "log-2026-02-06.txt"
            // LocalDate.ofEpochDay is efficient here
            val dateStr = LocalDate.ofEpochDay(currentDayCached).toString();
            val fileName = dateStr + ".log";
            val filePath = rootDirectory.resolve(fileName);

            // Ensure the parent directory exists
            if (!Files.exists(rootDirectory)) {
                Files.createDirectories(rootDirectory);
            }

            // Create new writer with buffer and append
            currentWriter = Files.newBufferedWriter(
                    filePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            );

        } catch (IOException exception) {
            System.err.println(exception);
            System.exit(15);
        }
    }

    private void write(BufferedWriter writer, String message) {
        try {
            Ansi.writeStripped(writer, message);
        } catch (IOException exception) {
            System.err.println(exception);
            System.exit(16);
        }
    }

    private void flush(BufferedWriter writer) {
        try { writer.flush(); }
        catch (IOException e) {
            // Ignored during shutdown or rotation
        }
    }

    private void close(@org.jspecify.annotations.Nullable BufferedWriter writer) {
        if (writer == null) return;

        try { writer.close(); }
        catch (IOException e) {
            // Ignored during shutdown or rotation
        }
    }
}
