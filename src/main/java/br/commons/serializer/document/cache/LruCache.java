package br.commons.serializer.document.cache;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * LRU cache with time window: each entry is valid for {@code ttlNanos} since it was written and the map stores at most
 * {@code size} entries, evicting the least recently used. Stores {@code null} (an absent value is also a response).
 *
 * <p>Hotpath: {@link #lookup} does not allocate, does not use lambda, and does not construct key — returns {@link #MISS}
 * when there is no valid entry, and the caller decides to calculate and {@link #put save}. Two readers can calculate
 * the same value concurrently; since all values stored here are immutable, the second only overwrites the first.
 */
@NullMarked
public final class LruCache<K, V> {

    /** Response from {@link #lookup} when there is no valid entry for the key. */
    public static final Object MISS = new Object();

    private static final Object NULL = new Object();

    private record Slot(Object value, long expiresAt) {}

    private final long ttlNanos;
    private final LongSupplier clock;
    private final boolean enabled;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<K, Slot> entries;

    public LruCache(int size, long ttlNanos, LongSupplier clock) {
        this.ttlNanos = ttlNanos;
        this.clock = clock;
        this.enabled = size > 0 && ttlNanos > 0;
        this.entries = new LinkedHashMap<>(Math.max(size, 1), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Slot> eldest) {
                return size() > size;
            }
        };
    }

    /** With parameters from {@link CacheSettings} and the system clock. */
    public static <K, V> LruCache<K, V> standard() {
        return new LruCache<>(CacheSettings.SIZE, CacheSettings.TTL_NANOS, System::nanoTime);
    }

    /** The stored value (which can be {@code null}) or {@link #MISS}. */
    public @Nullable Object lookup(K key) {
        if (!enabled) return MISS;

        lock.lock();
        try {
            val slot = entries.get(key);
            if (slot == null) return MISS;
            if (clock.getAsLong() - slot.expiresAt() >= 0) {
                entries.remove(key);
                return MISS;
            }
            return slot.value() == NULL ? null : slot.value();
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, @Nullable V value) {
        if (!enabled) return;

        val slot = new Slot(value == null ? NULL : value, clock.getAsLong() + ttlNanos);
        lock.lock();
        try {
            entries.put(key, slot);
        } finally {
            lock.unlock();
        }
    }

    public void invalidate(K key) {
        lock.lock();
        try {
            entries.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            entries.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }
}
