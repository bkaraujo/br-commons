package br.commons.cache;

import br.commons.i18n.Errors;

import br.commons.Logger;
import br.commons.Result;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Single thread that serializes all off-heap cache maintenance. The public API returns
 * {@link Result} instead of throwing: whoever publishes the event is inside its own transaction and cannot
 * be crashed by a cache failure.
 *
 * <p>The verdict comes <b>inside</b> a {@link CompletableFuture} because the task's exception only
 * exists later, in the worker thread: returning a synchronous {@code Result} would be false success.
 * Those who only enqueue ignore the future; those who need to know (tests, diagnostics) wait for it.
 */
@NullMarked
public final class CacheWorker {
    private static final long AWAIT_SECONDS = 10;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(
            r -> { val t = new Thread(r, "cache-worker"); t.setDaemon(true); return t; });

    private CacheWorker() {}

    /** Never lets an exception escape: the event publisher is inside its own transaction. */
    public static CompletableFuture<Result<Void, String>> submit(Runnable task) {
        try {
            return CompletableFuture.runAsync(task, WORKER).handle(CacheWorker::verdict);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(rejected(e));
        }
    }

    /**
     * Like {@link #submit}, for tasks that already know how to report success: its {@code Result}
     * passes through intact, without throwing an exception just so {@link #verdict} can reconstruct the message.
     */
    public static CompletableFuture<Result<Void, String>> submitResult(Supplier<Result<Void, String>> task) {
        return supply(task).thenApply(outcome -> outcome.flatMap(inner -> inner));
    }

    public static <T> CompletableFuture<Result<T, String>> supply(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, WORKER).handle(CacheWorker::verdict);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(rejected(e));
        }
    }

    public static Result<Void, String> awaitIdle() {
        WORKER.shutdown();
        try {
            if (!WORKER.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                WORKER.shutdownNow();
                return Result.failure(Errors.get().cacheWorkerNotTerminated(String.valueOf(AWAIT_SECONDS)).value());
            }
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            WORKER.shutdownNow();
            return Result.failure(e.toString());
        }
    }

    /**
     * Waits for all pending tasks to complete WITHOUT shutting down the worker (for tests). The marker
     * task is FIFO: when it runs, all previous tasks have already run.
     */
    public static Result<Void, String> waitForPending() {
        try {
            return submit(() -> {}).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(e.toString());
        } catch (ExecutionException | TimeoutException e) {
            return Result.failure(Errors.get().cacheWorkerBusy(e).value());
        }
    }

    /** Converts the pair (value, exception) from {@code handle} into the task's verdict. */
    private static <T> Result<T, String> verdict(@Nullable T value, @Nullable Throwable thrown) {
        if (thrown == null) return Result.success(value);

        val cause = thrown instanceof CompletionException && thrown.getCause() != null
                ? thrown.getCause()
                : thrown;
        Logger.error("Cache worker task failed: %s", cause.toString());
        return Result.failure(cause.toString());
    }

    private static <T> Result<T, String> rejected(RejectedExecutionException e) {
        Logger.error("Cache worker rejected task: %s", e.toString());
        return Result.failure(e.toString());
    }
}
