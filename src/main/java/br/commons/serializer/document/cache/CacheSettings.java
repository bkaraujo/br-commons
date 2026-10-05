package br.commons.serializer.document.cache;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.TimeUnit;

/**
 * Parameters for read caches, read <b>once</b> during class loading: changing requires restarting the application.
 * <ul>
 *   <li>{@code br.commons.serializer.cache.ttl.seconds} — time window for each entry (default {@value #DEFAULT_TTL_SECONDS});</li>
 *   <li>{@code br.commons.serializer.cache.size} — entries per cache before LRU eviction (default {@value #DEFAULT_SIZE}).</li>
 * </ul>
 * Either value {@code <= 0} disables the cache: all reads go to the tree.
 */
@NullMarked
public final class CacheSettings {

    public static final String TTL_PROPERTY = "br.commons.serializer.cache.ttl.seconds";
    public static final String SIZE_PROPERTY = "br.commons.serializer.cache.size";

    static final long DEFAULT_TTL_SECONDS = 60;
    static final int DEFAULT_SIZE = 256;

    public static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(Long.getLong(TTL_PROPERTY, DEFAULT_TTL_SECONDS));
    public static final int SIZE = Integer.getInteger(SIZE_PROPERTY, DEFAULT_SIZE);

    private CacheSettings() {}
}
