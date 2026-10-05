package br.commons.logger.channel;

import br.commons.Threads;
import br.commons.logger.LogChannel;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Log to one file, written by a daemon thread every 100 ms. Appends by default ({@link Mode#TRUNCATE} starts the
 * file empty). The queue is drained and the file closed on JVM shutdown or {@link #close()}.
 */
@NullMarked
public final class FileChannel implements LogChannel {

    public enum Mode { APPEND, TRUNCATE }

    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;
    private final CountDownLatch finished = new CountDownLatch(1);

    public FileChannel(Path path) {
        this(path, Mode.APPEND);
    }

    public FileChannel(Path path, Mode mode) {
        Threads.daemon("FileChannel[" + path.getFileName() + "]", () -> run(path, mode));
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "FileChannel-shutdown"));
    }

    /** Writes what is queued and closes the file (waits up to 2 s); later messages are not written. */
    public void close() {
        running = false;
        try {
            finished.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void write(String message) {
        queue.offer(message);
    }

    private void run(Path path, Mode mode) {
        try {
            val writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    mode == Mode.APPEND ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    drain(writer);
                    Threads.sleep(100);
                }
            } finally {
                try {
                    drain(writer);
                    writer.close();
                } catch (IOException e) {
                    // Ignorado durante encerramento
                }
            }
        } catch (IOException e) {
            System.err.println("[FileChannel] Falha ao abrir " + path + ": " + e.getMessage());
        } finally {
            finished.countDown();
        }
    }

    private void drain(BufferedWriter writer) throws IOException {
        String message;
        while ((message = queue.poll()) != null) {
            Ansi.writeStripped(writer, message);
        }
        writer.flush();
    }
}
