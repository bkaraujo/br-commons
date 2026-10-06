package br.commons;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

@NullMarked
public abstract class Threads {
    private Threads() {}

    /**
     * {@link Thread#sleep} without the checked exception. An interruption ends the sleep early and the interrupt
     * flag is restored, so loops must check {@code Thread.currentThread().isInterrupted()} to stop.
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@link CountDownLatch#await} without the checked exception; same interruption rule as {@link #sleep}. */
    public static void wait(CountDownLatch letch) {
        try { letch.await(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void locked(Lock lock, Runnable runnable) {
        lock.lock();
        try { runnable.run(); }
        finally { lock.unlock(); }
    }

    @SuppressWarnings("unchecked")
    public static <T> T locked(Lock lock, Callable<T> runnable) {
        lock.lock();

        try { return runnable.call(); }
        catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            // Adaptation from the POC (spec §2.4): an exception becomes an exception, not the end of the JVM.
            throw new IllegalStateException(exception);
        } finally { lock.unlock(); }
    }

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    public static void virtual(Runnable task) { executor.submit(task); }
    public static <T> Future<@Nullable T> virtual(Callable<T> task) { return executor.submit(task); }

    public static Thread daemon(Runnable runnable){ return hardware(null, true, runnable); }
    public static Thread daemon(String name, Runnable runnable){ return hardware(name, true, runnable); }
    public static Thread detached(Runnable runnable){ return hardware(null, false, runnable); }
    public static Thread detached(String name, Runnable runnable){ return hardware(name, false, runnable); }
    private static Thread hardware(@Nullable String name, boolean daemon, Runnable runnable) {
        val thread = new Thread(runnable);
        if (name != null) thread.setName(name);

        Logger.debug("Starting thread %s %s", daemon ? "daemon" : Strings.EMPTY, thread.getName());

        thread.setDaemon(daemon);
        thread.start();

        return thread;
    }

    public static String name() { return Thread.currentThread().getName(); }
    public static void name(String name) {
        if (name.trim().isEmpty()) return;
        Thread.currentThread().setName(name);
    }

    public static void interrupt() { interrupt(Thread.currentThread()); }

    @SuppressWarnings("EmptyCatch")
    public static void interrupt(Thread thread) {
        if (thread.isInterrupted()) return;
        Threads.virtual(() -> {
            try { thread.interrupt(); }
            catch (Throwable ignored) {}
        });
    }

    @Nullable
    public static <T> T result(Future<T> task) {
        try { return task.get(); }
        catch (Exception exception) {
            Logger.error("Failed to complete task: %s", exception);
            return null;
        }
    }
}
