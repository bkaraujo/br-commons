package br.commons.logger.channel;

import br.commons.Platform;
import br.commons.Threads;
import br.commons.logger.LogChannel;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@NullMarked
public final class RollingFileChannel implements LogChannel {
    private final Queue<String> messages = new ConcurrentLinkedQueue<>();

    private final RollingFileWriter writer;

    public RollingFileChannel() {
        this(Platform.fileSystem().jvmFS());
    }

    /** One file per day ({@code yyyy-MM-dd.log}) under {@code directory}; the queue is drained on JVM shutdown. */
    public RollingFileChannel(Path directory) {
        writer = new RollingFileWriter(messages, directory);
        Threads.virtual(writer);
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "RollingFileChannel-shutdown"));
    }

    /** Writes what is queued and closes the file; later messages are not written. */
    public void close() {
        writer.stop(2_000);
    }

    @Override
    public void write(String message) {
        messages.add(message);
    }

}
