package br.commons.platform;

import br.commons.Logger;
import br.commons.Result;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Off-heap Arena indexed by key. Every operation that can fail — cache already closed, key
 * absent — returns {@link Result}: the public API does not throw nor deliver {@code null} for the
 * caller to decide what to do. The exception is {@link #segment}, the hot path access, where a
 * {@code Result} per read would be an allocation per line read. The static codecs also remain
 * outside this rule on purpose: they are pure serialization primitives, with defined loss
 * (truncate/saturate + log), consumed by {@code Layout.write} through a {@code BiConsumer}.
 *
 * <p>Generic in key: an instance holds only one type of record, so the natural key is the id
 * of the record ({@code UUID}), not a String with type prefix. Filtering by prefix in here
 * was a {@code startsWith} per record that never discarded anything.
 */
@NullMarked
public class NativeCache<K> implements AutoCloseable {
    private static final LocalDateTime EPOCH_UTC = LocalDateTime.of(1970, 1, 1, 0, 0);

    /** Error returned by any operation after {@link #close()}. */
    public static final String ERROR_CLOSED = "NativeCache is closed";

    /** Error for absent key. Constant: miss is routine, doesn't pay concatenation per call. */
    public static final String ERROR_MISS = "NativeCache miss";

    public static final long NULL_LONG = Long.MIN_VALUE;
    public static final int  NULL_INT  = Integer.MIN_VALUE;

    /** Grace before closing an arena replaced by {@link #compact}: readers still holding its segments finish. */
    public static final Duration COMPACT_GRACE = Duration.ofSeconds(1);

    /** Replaced by {@link #compact}; volatile so {@link #put} always allocates in the current one. */
    private volatile Arena arena;
    /** Arenas replaced by {@link #compact} and not closed yet (grace period); closed at the latest by {@link #close}. */
    private final Set<Arena> retired = ConcurrentHashMap.newKeySet();
    /** Bytes allocated in the current arena, live or dead. */
    private final AtomicLong allocatedBytes = new AtomicLong();
    /** Bytes of segments no longer indexed (removed or overwritten): memory only {@link #compact} gives back. */
    private final AtomicLong deadBytes = new AtomicLong();
    private final Map<K, MemorySegment> index = new ConcurrentHashMap<>();
    /** Insertion order of keys — the index is hash, and listing without stable order is unstable listing. */
    private final Queue<K> order = new ConcurrentLinkedQueue<>();
    private volatile boolean closed = false;

    public NativeCache() {
        arena = Arena.ofShared();
    }

    /** Allocates (always a new, zeroed segment) and indexes at {@code key}. */
    public Result<MemorySegment, String> put(K key, long size) {
        if (closed) return Result.failure(ERROR_CLOSED);
        // Arena.allocate already zeroes the segment: no heap copy, no int narrowing.
        val segment = arena.allocate(size);
        allocatedBytes.addAndGet(size);
        // Overwriting a key keeps the original position: only new key enters at the end of order.
        val previous = index.put(key, segment);
        if (previous == null) order.add(key);
        else deadBytes.addAndGet(previous.byteSize());
        return Result.success(segment);
    }

    /** Fails on miss: absence of key is normal result, but never a usable segment. */
    public Result<MemorySegment, String> get(K key) {
        if (closed) return Result.failure(ERROR_CLOSED);
        val segment = index.get(key);
        if (segment == null) return Result.failure(ERROR_MISS);
        return Result.success(segment);
    }

    /**
     * Hot path access: the segment, or {@code null} if the key is not here (or the cache already
     * closed). Same read as {@link #get}, without the {@link Result} — which in a listing or find
     * per line becomes an allocation per read, with nothing to report beyond the absence itself.
     */
    public @Nullable MemorySegment segment(K key) {
        if (closed) return null;
        return index.get(key);
    }

    /**
     * Visits each indexed segment, in insertion order. One {@code Result} for the entire traversal,
     * not one per record: this is the zero-alloc read path of flyweights
     * ({@code Layout.View}), where wrapping record by record would nullify the gain.
     */
    public Result<Void, String> forEach(BiConsumer<K, MemorySegment> visitor) {
        if (closed) return Result.failure(ERROR_CLOSED);
        for (val key : order) {
            val segment = index.get(key);
            if (segment != null) visitor.accept(key, segment);
        }
        return Result.success();
    }

    public Result<Void, String> remove(K key) {
        if (closed) return Result.failure(ERROR_CLOSED);
        val removed = index.remove(key);
        if (removed != null) {
            order.remove(key);
            deadBytes.addAndGet(removed.byteSize());
        }
        return Result.success();
    }

    public long allocatedBytes() {
        return allocatedBytes.get();
    }

    public long deadBytes() {
        return deadBytes.get();
    }

    /** Whether dead segments are more than half of the allocated bytes — the point where {@link #compact} pays. */
    public boolean needsCompaction() {
        return !closed && deadBytes.get() > allocatedBytes.get() / 2;
    }

    /**
     * Copies the live segments to a new arena and indexes the copies, then closes the old arena after
     * {@code grace}: memory becomes proportional to the live records again. A reader that still holds an old
     * segment keeps working during the grace; after it, the access throws {@link IllegalStateException} and the
     * reader must look the key up again ({@code AbstractCache} does). Must not run concurrently with
     * {@link #put}/{@link #remove} (single writer, e.g. the cache worker).
     */
    public Result<Void, String> compact(Duration grace) {
        if (closed) return Result.failure(ERROR_CLOSED);
        val fresh = Arena.ofShared();
        long live = 0;
        for (val key : order) {
            val segment = index.get(key);
            if (segment == null) continue;
            val copy = fresh.allocate(segment.byteSize());
            copy.copyFrom(segment);
            index.put(key, copy);
            live += segment.byteSize();
        }
        val old = arena;
        arena = fresh;
        allocatedBytes.set(live);
        deadBytes.set(0);
        retired.add(old);
        Thread.ofVirtual().name("native-cache-retire").start(() -> {
            try {
                Thread.sleep(grace);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            retire(old);
        });
        return Result.success();
    }

    private void retire(Arena old) {
        if (!retired.remove(old)) return; // close() got there first
        try {
            old.close();
        } catch (IllegalStateException busy) {
            Logger.warn("Retired arena still in use, closing on NativeCache.close(): %s", busy.toString());
            retired.add(old);
        }
    }

    /** Indexed keys, in insertion order. */
    public Result<List<K>, String> keys() {
        if (closed) return Result.failure(ERROR_CLOSED);
        val result = new ArrayList<K>();
        for (val key : order) {
            if (index.containsKey(key)) {
                result.add(key);
            }
        }
        return Result.success(result);
    }

    public Result<Integer, String> size() {
        if (closed) return Result.failure(ERROR_CLOSED);
        return Result.success(index.size());
    }

    /** Number of indexed records; {@code 0} after {@link #close()}. Without {@link Result}
     *  because it exists to size the destination collection before a traversal. */
    public int count() {
        return closed ? 0 : index.size();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        arena.close();
        for (val old : List.copyOf(retired)) {
            retired.remove(old);
            try { old.close(); } catch (IllegalStateException e) { Logger.warn("Retired arena close failed: %s", e.toString()); }
        }
        index.clear();
        order.clear();
    }

    // Codecs

    public static void writeUuid(MemorySegment s, long off, @Nullable UUID v) {
        if (v == null) {
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, 0L);
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off + 8, 0L);
        } else {
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, v.getMostSignificantBits());
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off + 8, v.getLeastSignificantBits());
        }
    }

    public static long readUuidMsb(MemorySegment s, long off) {
        return s.get(ValueLayout.JAVA_LONG_UNALIGNED, off);
    }

    public static long readUuidLsb(MemorySegment s, long off) {
        return s.get(ValueLayout.JAVA_LONG_UNALIGNED, off + 8);
    }

    public static @Nullable UUID readUuid(MemorySegment s, long off) {
        val msb = s.get(ValueLayout.JAVA_LONG_UNALIGNED, off);
        val lsb = s.get(ValueLayout.JAVA_LONG_UNALIGNED, off + 8);
        if (msb == 0 && lsb == 0) return null;
        return new UUID(msb, lsb);
    }

    public static void writeString(MemorySegment s, long off, int capacity, @Nullable String v) {
        if (v == null) {
            s.set(ValueLayout.JAVA_INT_UNALIGNED, off, -1);
        } else if (v.isEmpty()) {
            s.set(ValueLayout.JAVA_INT_UNALIGNED, off, 0);
        } else {
            val bytes = v.getBytes(StandardCharsets.UTF_8);
            int len = bytes.length;
            val available = capacity - 4;
            if (len > available) {
                len = truncateUtf8(bytes, available);
                Logger.warn("String truncated from %s to %s bytes", bytes.length, len);
            }
            s.set(ValueLayout.JAVA_INT_UNALIGNED, off, len);
            s.asSlice(off + 4, len).copyFrom(MemorySegment.ofArray(bytes).asSlice(0, len));
        }
    }

    public static @Nullable String readString(MemorySegment s, long off) {
        val len = s.get(ValueLayout.JAVA_INT_UNALIGNED, off);
        if (len < 0) return null;
        if (len == 0) return "";
        val bytes = new byte[len];
        MemorySegment.copy(s, off + 4, MemorySegment.ofArray(bytes), 0, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeBoolean(MemorySegment s, long off, boolean v) {
        s.set(ValueLayout.JAVA_BYTE, off, v ? (byte) 1 : (byte) 0);
    }

    public static boolean readBoolean(MemorySegment s, long off) {
        return s.get(ValueLayout.JAVA_BYTE, off) != 0;
    }

    public static void writeMoney(MemorySegment s, long off, @Nullable BigDecimal v) {
        if (v == null) {
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, NULL_LONG);
        } else {
            try {
                val cents = v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
                s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, cents);
            } catch (ArithmeticException e) {
                Logger.warn("BigDecimal overflow: %s", v);
                s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, NULL_LONG);
            }
        }
    }

    public static long readMoneyCents(MemorySegment s, long off) {
        return s.get(ValueLayout.JAVA_LONG_UNALIGNED, off);
    }

    public static void writeTimestamp(MemorySegment s, long off, @Nullable LocalDateTime v) {
        if (v == null) {
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, NULL_LONG);
        } else {
            val millis = ChronoUnit.MILLIS.between(EPOCH_UTC, v);
            s.set(ValueLayout.JAVA_LONG_UNALIGNED, off, millis);
        }
    }

    public static long readTimestampMillis(MemorySegment s, long off) {
        return s.get(ValueLayout.JAVA_LONG_UNALIGNED, off);
    }

    /**
     * Longest prefix of at most {@code maxLen} bytes that ends on a character boundary. {@code bytes[len]} is the
     * first byte left out: while it is a continuation byte the cut is inside a character, so the cut moves back
     * to that character's lead byte, which is left out too.
     */
    private static int truncateUtf8(byte[] bytes, int maxLen) {
        if (bytes.length <= maxLen) return bytes.length;
        int len = Math.max(maxLen, 0);
        while (len > 0 && (bytes[len] & 0xC0) == 0x80) {
            len--;
        }
        return len;
    }
}
