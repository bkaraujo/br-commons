package br.commons.cache;

import br.commons.Logger;
import br.commons.Result;
import br.commons.module.Lifecycle;
import br.commons.business.BusinessError;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Off-heap cache as an application module, the way {@code WebServer} is the HTTP one: each {@link CacheRegion} is
 * registered with {@link #register(CacheRegion)}, which hands back its {@link Cache} — reads go straight to memory,
 * writes to the server's single {@code cache-worker} thread.
 *
 * <p>As a {@link Lifecycle}, {@link #initialize()} starts the worker thread (not daemon, it keeps the process alive)
 * and {@link #terminate()} runs the pending writes, releases every region (its partitions are unloaded, and from then
 * on its reads and writes return {@code Failure} {@code cacheRegionClosed}) and ends the thread. Before
 * {@link #initialize()}, writes return {@code Failure}; registering is allowed at any time.
 */
@NullMarked
public final class CacheServer implements Lifecycle {

    private final CacheWorker worker = new CacheWorker();
    private final List<Cache<?>> caches = new CopyOnWriteArrayList<>();

    public <T> Cache<T> register(CacheRegion<T> region) {
        val cache = new Cache<>(worker, region);
        caches.add(cache);
        return cache;
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        worker.start();
        return Result.success();
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        worker.waitForPending().ifFailure(error -> Logger.warn("Cache server: %s", error));
        for (val cache : caches) {
            cache.close().join().ifFailure(error -> Logger.warn("Cache unload failed: %s", error));
        }
        caches.clear();
        return worker.stop().mapFailure(BusinessError.Infrastructure::new);
    }

    /** Waits for the writes already sent (tests, diagnostics), without stopping. */
    public Result<Void, String> waitForPending() {
        return worker.waitForPending();
    }
}
