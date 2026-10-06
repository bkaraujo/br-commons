package br.commons.cache;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Result;
import br.commons.chrono.Time;
import br.commons.debug.Execution;
import br.commons.locale.i18n.Errors;
import br.commons.logger.LogLevel;
import br.commons.platform.NativeCache;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Proxy of one {@link CacheRegion} inside the {@link CacheServer}: reads straight from the off-heap memory, writes
 * on the server's worker thread. The region is split into independent partitions, an opaque {@code String} chosen by
 * the caller (tenant, account, user...): {@link #load} fills one from {@link CacheRegion#load}, {@link #unload} frees
 * its native memory, and every read or mutation targets one partition. Queries ({@link #find}, {@link #list}) are hot
 * path: straight code, no lambda, no key building and no instrumentation — the timer only exists if DEBUG is
 * on for this class. Mutations are serialized in the {@link CacheWorker} and timed
 * by {@link Execution}, which is cheap when the level doesn't pass.
 */
@NullMarked
public final class Cache<T> {
    private static final long POPULATE_TIMEOUT_SECONDS = 2;

    /** Class for the log gate: constant, so {@link Logger#enabled(LogLevel, String)} doesn't have
     *  to scan the stack looking for the caller at each query. */
    private static final String CALLER = Meta.fqn(Cache.class);

    private final CacheWorker worker;
    private final CacheRegion<T> region;
    private final String prefix;
    /** Set by {@link #close()} when the {@link CacheServer} terminates: reads and writes fail from then on. */
    private volatile boolean closed;

    private final ConcurrentHashMap<String, CompletableFuture<NativeCache<UUID>>> registry = new ConcurrentHashMap<>();

    Cache(CacheWorker worker, CacheRegion<T> region) {
        this.worker = worker;
        this.region = region;
        this.prefix = region.name() + ":";
    }

    /**
     * Fills the partition on the worker. Before {@link CacheServer#initialize()} it fails at once, registering
     * nothing: a partition registered without a worker to fill it would hold its readers for the populate timeout.
     */
    public CompletableFuture<Result<Void, String>> load(String partition) {
        if (closed) return CompletableFuture.completedFuture(closedFailure());
        if (!worker.running()) {
            return CompletableFuture.completedFuture(Result.failure(Errors.get().cacheWorkerNotRunning().value()));
        }
        return Execution.nanos(CALLER, () -> {
            val future = new CompletableFuture<NativeCache<UUID>>();
            val previous = registry.put(partition, future);
            // Reloading a partition replaces its cache: the old one is closed once its populate is done, or its
            // native memory would leak. Readers still holding it re-read from the registry (see tolerantFind).
            if (previous != null) previous.thenAccept(NativeCache::close);

            return worker.submit(() -> {
                val cache = new NativeCache<UUID>();
                val errors = new ArrayList<String>();
                try {
                    for (val model : region.load(partition)) {
                        store(cache, model).ifFailure(errors::add);
                    }
                } finally {
                    future.complete(cache);
                }
                return errors.isEmpty()
                        ? Result.success()
                        : Result.failure(Errors.get().cachePopulateIncomplete(partition, errors).value());
            });
        });
    }

    public CompletableFuture<Result<Void, String>> unload(String partition) {
        return Execution.nanos(CALLER, () -> {
            val future = registry.remove(partition);
            if (future == null) return CompletableFuture.completedFuture(Result.success());

            return future.handle((cache, thrown) -> {
                if (thrown != null) return Result.failure(thrown.toString());
                try {
                    cache.close();
                    return Result.success();
                } catch (Exception e) {
                    Logger.warn("Cache close failed: %s", e.toString());
                    return Result.failure(e.toString());
                }
            });
        });
    }

    /** Releases the region for good (called by {@link CacheServer#terminate()}): every partition is unloaded. */
    CompletableFuture<Result<Void, String>> close() {
        closed = true;
        return unloadAll();
    }

    /** Unloads every partition; the first failure, if any, is the outcome. */
    public CompletableFuture<Result<Void, String>> unloadAll() {
        return firstFailure(registry.keySet().stream().map(this::unload).toList());
    }

    public CompletableFuture<Result<Void, String>> upsert(String partition, T model) {
        return Execution.nanos(CALLER, () -> onCache(partition, cache -> compacting(cache, store(cache, model))));
    }

    public CompletableFuture<Result<Void, String>> evict(String partition, UUID id) {
        return Execution.nanos(CALLER, () -> onCache(partition, cache -> compacting(cache, cache.remove(id))));
    }

    /**
     * After a mutation, on the worker (the single writer): when dead segments pass half of the arena, copy the live
     * ones to a new arena. Upserts and evictions of a long-lived partition otherwise grow it without bound.
     */
    private static Result<Void, String> compacting(NativeCache<UUID> cache, Result<Void, String> mutation) {
        if (mutation.isFailure() || !cache.needsCompaction()) return mutation;
        return cache.compact(NativeCache.COMPACT_GRACE);
    }

    public CompletableFuture<Result<Void, String>> evictEverywhere(UUID id) {
        return Execution.nanos(CALLER, () -> {
            val pending = registry.keySet().stream()
                    .map(partition -> onCache(partition, cache -> compacting(cache, cache.remove(id))))
                    .toList();
            return firstFailure(pending);
        });
    }

    private static CompletableFuture<Result<Void, String>> firstFailure(List<CompletableFuture<Result<Void, String>>> pending) {
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> pending.stream()
                        .map(CompletableFuture::join)
                        .filter(Result::isFailure)
                        .findFirst()
                        .orElse(Result.success()));
    }

    public Result<List<T>, String> list(String partition) {
        if (closed) return closedFailure();
        if (!Logger.enabled(LogLevel.DEBUG, CALLER)) return tolerantList(partition);

        val start = Time.nanos();
        try {
            return tolerantList(partition);
        } finally {
            Logger.debug("list time: %s ns", Time.nanos() - start);
        }
    }

    public Result<T, String> find(String partition, UUID id) {
        if (closed) return closedFailure();
        if (!Logger.enabled(LogLevel.DEBUG, CALLER)) return tolerantFind(partition, id);

        val start = Time.nanos();
        try {
            return tolerantFind(partition, id);
        } finally {
            Logger.debug("find time: %s ns", Time.nanos() - start);
        }
    }

    /**
     * A reader may still hold a cache that {@link #unload} (or a replacement) closes under it: the access to the
     * closed arena throws {@link IllegalStateException}. The read is repeated once against what the registry holds
     * now; only a second failure (or an unloaded partition) is absence. The {@code try} costs nothing on the hot path.
     */
    private Result<List<T>, String> tolerantList(String partition) {
        try {
            return doList(partition);
        } catch (IllegalStateException closed) {
            try {
                return doList(partition);
            } catch (IllegalStateException again) {
                return this.closed ? closedFailure() : Result.success(List.of());
            }
        }
    }

    private Result<T, String> tolerantFind(String partition, UUID id) {
        try {
            return doFind(partition, id);
        } catch (IllegalStateException closed) {
            try {
                return doFind(partition, id);
            } catch (IllegalStateException again) {
                return this.closed ? closedFailure() : notFound(id);
            }
        }
    }

    private Result<List<T>, String> doList(String partition) {
        val cache = live(partition);
        if (cache != null) return collect(cache);

        return switch (awaited(partition)) {
            case Result.Failure(var error) -> Result.failure(error);
            case Result.Success(var resolved) -> resolved == null
                    ? Result.success(List.of())
                    : collect(resolved);
        };
    }

    private Result<T, String> doFind(String partition, UUID id) {
        val cache = live(partition);
        if (cache != null) return lookup(cache, id);

        return switch (awaited(partition)) {
            case Result.Failure(var error) -> Result.failure(error);
            case Result.Success(var resolved) -> resolved == null
                    ? notFound(id)
                    : lookup(resolved, id);
        };
    }

    private Result<T, String> lookup(NativeCache<UUID> cache, UUID id) {
        val segment = cache.segment(id);
        return segment == null ? notFound(id) : Result.success(region.read(segment));
    }

    private Result<List<T>, String> collect(NativeCache<UUID> cache) {
        val models = new ArrayList<T>(cache.count());
        val traversal = cache.forEach((_, segment) -> models.add(region.read(segment)));
        if (traversal instanceof Result.Failure<Void, String>(String error)) return Result.failure(error);
        return Result.success(models);
    }

    /** Only on miss: the message costs a {@code UUID.toString} and a concatenation, and this is where the
     *  {@code prefix} survives — in the key it was redundant, each cache has only its own type. */
    private <R> Result<R, String> closedFailure() {
        return Result.failure(Errors.get().cacheRegionClosed(region.name()).value());
    }

    private Result<T, String> notFound(UUID id) {
        return Result.failure(Errors.get().cacheNotFound(prefix + id).value());
    }

    /** Cache ready for immediate reading, or {@code null} if absent, still populating or closed. */
    private @Nullable NativeCache<UUID> live(String partition) {
        val future = registry.get(partition);
        if (future == null) return null;

        val cache = future.getNow(null);
        return cache == null || cache.isClosed() ? null : cache;
    }

    /**
     * Rare path, outside the hotpath: partition not loaded, or populate still in flight right after
     * {@link #load}. Can wait, allocate and fail. {@code Success} with {@code null} is absence — the
     * caller decides what absence means for them.
     */
    private Result<@Nullable NativeCache<UUID>, String> awaited(String partition) {
        val future = registry.get(partition);
        if (future == null) return Result.success(null);

        try {
            val cache = future.get(POPULATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return cache.isClosed() ? Result.success(null) : Result.success(cache);
        } catch (TimeoutException e) {
            Logger.debug("Cache populate timeout for %s", partition);
            return Result.failure(Errors.get().cachePopulateTimeout(partition).value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(Errors.get().cacheReadInterrupted(partition).value());
        } catch (Exception e) {
            Logger.warn("Cache read failed for %s: %s", partition, e.toString());
            return Result.failure(e.toString());
        }
    }

    private CompletableFuture<Result<Void, String>> onCache(String partition, Function<NativeCache<UUID>, Result<Void, String>> action) {
        if (closed) return CompletableFuture.completedFuture(closedFailure());
        val future = registry.get(partition);
        if (future == null) return CompletableFuture.completedFuture(Result.success());

        return worker.submit(() -> {
            val cache = future.getNow(null);
            if (cache == null || cache.isClosed()) return Result.success();
            return action.apply(cache);
        });
    }

    private Result<Void, String> store(NativeCache<UUID> cache, T model) {
        return cache.put(region.key(model), region.size(model))
                .flatMap(segment -> {
                    region.write(segment, model);
                    return Result.success();
                });
    }
}
