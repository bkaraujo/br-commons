package br.commons.serializer.document.cache;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LruCacheTest {

    private final AtomicLong now = new AtomicLong();

    private LruCache<String, String> cache(int size, long ttl) {
        return new LruCache<>(size, ttl, now::get);
    }

    @Test
    void hitInsideTheWindowMissAfterIt() {
        val cache = cache(4, 10);
        cache.put("a", "1");
        now.set(9);
        assertEquals("1", cache.lookup("a"));
        now.set(10);
        assertSame(LruCache.MISS, cache.lookup("a"));
        assertEquals(0, cache.size());
    }

    @Test
    void evictsTheLeastRecentlyUsed() {
        val cache = cache(2, 100);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.lookup("a");
        cache.put("c", "3");
        assertEquals("1", cache.lookup("a"));
        assertSame(LruCache.MISS, cache.lookup("b"));
        assertEquals("3", cache.lookup("c"));
    }

    @Test
    void nullIsAnAnswer() {
        val cache = cache(2, 100);
        cache.put("a", null);
        assertNull(cache.lookup("a"));
        assertSame(LruCache.MISS, cache.lookup("b"));
    }

    @Test
    void zeroDisables() {
        val bySize = cache(0, 100);
        bySize.put("a", "1");
        assertSame(LruCache.MISS, bySize.lookup("a"));

        val byTtl = cache(2, 0);
        byTtl.put("a", "1");
        assertSame(LruCache.MISS, byTtl.lookup("a"));
    }

    @Test
    void invalidateRemoves() {
        val cache = cache(2, 100);
        cache.put("a", "1");
        cache.invalidate("a");
        assertSame(LruCache.MISS, cache.lookup("a"));
    }
}
