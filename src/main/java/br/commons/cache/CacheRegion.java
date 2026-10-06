package br.commons.cache;

import org.jspecify.annotations.NullMarked;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.UUID;

/**
 * A region of the {@link CacheServer}: what it holds and how a model becomes bytes. Registered with
 * {@link CacheServer#register(CacheRegion)}, which hands back the {@link Cache} that reads and writes it.
 * {@link #size}, {@link #write} and {@link #read} share one binary layout: change the three together.
 */
@NullMarked
public interface CacheRegion<T> {

    /** Shows in messages, e.g. {@code CART} → "Not found: CART:&lt;id&gt;". */
    String name();

    /** Reads a partition from the source of truth (database, service...) when it is loaded. */
    List<T> load(String partition);

    UUID key(T model);

    /** Bytes of the record. */
    long size(T model);

    void write(MemorySegment segment, T model);

    T read(MemorySegment segment);
}
