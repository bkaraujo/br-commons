package br.commons.cache;

import br.commons.Logger;
import br.commons.Result;
import br.commons.locale.i18n.Errors;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The {@link CacheServer}'s single platform thread ({@code cache-worker}, not daemon), which serializes every write.
 * Returns {@link Result} instead of throwing: whoever publishes the event is inside its own transaction and cannot
 * be crashed by a cache failure.
 *
 * <p>The verdict comes <b>inside</b> a {@link CompletableFuture} because the task's exception only
 * exists later, in the worker thread: returning a synchronous {@code Result} would be false success.
 * Outside {@link #start()}..{@link #stop()} a task is refused with {@code Failure}.
 */
@NullMarked
final class CacheWorker {
    private static final long AWAIT_SECONDS = 10;

    private volatile @Nullable ExecutorService executor;

    synchronized void start() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(
                    r -> Thread.ofPlatform().name("cache-worker").daemon(false).unstarted(r));
        }
    }

    boolean running() {
        return executor != null;
    }

    /** Runs the task's {@code Result} on the worker; an exception becomes {@code Failure}. */
    CompletableFuture<Result<Void, String>> submit(Supplier<Result<Void, String>> task) {
        val running = executor;
        if (running == null) {
            return CompletableFuture.completedFuture(Result.failure(Errors.get().cacheWorkerNotRunning().value()));
        }
        try {
            return CompletableFuture.supplyAsync(task, running).handle(CacheWorker::verdict);
        } catch (RejectedExecutionException e) {
            Logger.error("Cache worker rejected task: %s", e.toString());
            return CompletableFuture.completedFuture(Result.failure(e.toString()));
        }
    }

    /** Waits for the pending tasks without stopping: the marker task is FIFO, the previous ones already ran. */
    Result<Void, String> waitForPending() {
        try {
            return submit(Result::success).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(e.toString());
        } catch (ExecutionException | TimeoutException e) {
            return Result.failure(Errors.get().cacheWorkerBusy(e).value());
        }
    }

    /** Runs what is queued and ends the thread; a new {@link #start()} creates another. */
    synchronized Result<Void, String> stop() {
        val running = executor;
        if (running == null) return Result.success();
        executor = null;
        running.shutdown();
        try {
            if (!running.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                running.shutdownNow();
                return Result.failure(Errors.get().cacheWorkerNotTerminated(String.valueOf(AWAIT_SECONDS)).value());
            }
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.shutdownNow();
            return Result.failure(e.toString());
        }
    }

    /** Converts the pair (value, exception) from {@code handle} into the task's verdict. */
    private static Result<Void, String> verdict(@Nullable Result<Void, String> value, @Nullable Throwable thrown) {
        if (thrown == null) return value == null ? Result.success() : value;

        val cause = thrown instanceof CompletionException && thrown.getCause() != null
                ? thrown.getCause()
                : thrown;
        Logger.error("Cache worker task failed: %s", cause.toString());
        return Result.failure(cause.toString());
    }
}
