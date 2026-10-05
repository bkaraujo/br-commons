package br.commons.serializer.document;

import br.commons.serializer.document.cache.CacheSettings;
import br.commons.serializer.document.cache.LruCache;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Global cache of already-read documents, by source — the normalized {@link Path} of the file, the classpath resource,
 * or the JSON text itself — with window and size from {@link CacheSettings}. A {@link Document} is immutable,
 * so the same instance serves all threads; code writing to the source calls {@link #invalidate}.
 */
@NullMarked
public final class DocumentCache {

    private static final LruCache<Object, Document> CACHE = LruCache.standard();

    private DocumentCache() {}

    /** The document from the source, read by {@code reader} only when not in cache or cache window has expired. */
    public static Document cached(Object source, Supplier<Document> reader) {
        val key = key(source);
        val hit = CACHE.lookup(key);
        if (hit != LruCache.MISS) return (Document) Objects.requireNonNull(hit);

        val document = reader.get();
        CACHE.put(key, document);
        return document;
    }

    public static void invalidate(Object source) {
        CACHE.invalidate(key(source));
    }

    private static Object key(Object source) {
        return source instanceof Path path ? path.toAbsolutePath().normalize() : source;
    }
}
