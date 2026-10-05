package br.commons.cache;

import br.commons.Result;
import br.commons.platform.NativeCache;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractCacheTest {

    private static final String EAST = "east";
    private static final String WEST = "west";

    private record Item(UUID id, long amount) {}

    /** Layout mínimo: uuid (16) + amount (8). */
    private static final long SIZE = 24;

    private static final class ItemCache extends AbstractCache<Item> {
        ItemCache(Map<String, List<Item>> source) {
            super("TEST:",
                    partition -> source.getOrDefault(partition, List.of()),
                    Item::id,
                    _ -> SIZE,
                    (segment, item) -> {
                        NativeCache.writeUuid(segment, 0, item.id());
                        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 16, item.amount());
                    });
        }

        @Override
        protected Item mapToDomain(MemorySegment segment) {
            return new Item(
                    Objects.requireNonNull(NativeCache.readUuid(segment, 0)),
                    segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 16));
        }
    }

    private static ItemCache loaded(String partition, List<Item> items) {
        val cache = new ItemCache(Map.of(partition, items));
        assertTrue(cache.load(partition).join().isSuccess());
        return cache;
    }

    private static Item item(long amount) {
        return new Item(UUID.randomUUID(), amount);
    }

    @Test
    void testFindHit() {
        val one = item(10);
        val two = item(20);
        val cache = loaded(EAST, List.of(one, two));

        assertEquals(two, cache.find(EAST, two.id()).get());
        assertEquals(one, cache.find(EAST, one.id()).get());

        cache.unload(EAST).join();
    }

    @Test
    void testFindMissKeepsPrefixInMessage() {
        val stored = item(10);
        val cache = loaded(EAST, List.of(stored));
        val absent = UUID.randomUUID();

        assertEquals("not found: TEST:" + absent, failureOf(cache.find(EAST, absent)));

        cache.unload(EAST).join();
    }

    @Test
    void testFindOnUnloadedPartitionIsMiss() {
        val cache = new ItemCache(Map.of());
        val id = UUID.randomUUID();

        assertEquals("not found: TEST:" + id, failureOf(cache.find(EAST, id)));
    }

    @Test
    void testListKeepsLoadOrderAndIsMutable() {
        val one = item(10);
        val two = item(20);
        val three = item(30);
        val cache = loaded(EAST, List.of(one, two, three));

        val listed = cache.list(EAST).get();
        assertEquals(List.of(one, two, three), listed);

        // The list belongs to the caller: whoever lists usually sorts/filters on top of it.
        listed.remove(0);
        assertEquals(3, cache.list(EAST).get().size());

        cache.unload(EAST).join();
    }

    @Test
    void testListOnUnloadedPartitionIsEmpty() {
        val cache = new ItemCache(Map.of());

        val listed = cache.list(EAST);
        assertTrue(listed.isSuccess());
        assertTrue(listed.get().isEmpty());
    }

    @Test
    void testUpsertAndEvict() {
        val stored = item(10);
        val cache = loaded(EAST, List.of(stored));

        val added = item(99);
        assertTrue(cache.upsert(EAST, added).join().isSuccess());
        assertEquals(added, cache.find(EAST, added.id()).get());
        assertEquals(2, cache.list(EAST).get().size());

        val updated = new Item(stored.id(), 11);
        assertTrue(cache.upsert(EAST, updated).join().isSuccess());
        assertEquals(updated, cache.find(EAST, stored.id()).get());
        assertEquals(2, cache.list(EAST).get().size());

        assertTrue(cache.evict(EAST, stored.id()).join().isSuccess());
        assertTrue(cache.find(EAST, stored.id()).isFailure());
        assertEquals(List.of(added), cache.list(EAST).get());

        cache.unload(EAST).join();
    }

    @Test
    void testEvictEverywhereHitsEveryPartition() {
        val shared = item(10);
        val cache = new ItemCache(Map.of(EAST, List.of(shared), WEST, List.of(shared)));
        assertTrue(cache.load(EAST).join().isSuccess());
        assertTrue(cache.load(WEST).join().isSuccess());

        assertTrue(cache.evictEverywhere(shared.id()).join().isSuccess());

        assertTrue(cache.find(EAST, shared.id()).isFailure());
        assertTrue(cache.find(WEST, shared.id()).isFailure());

        cache.unload(EAST).join();
        cache.unload(WEST).join();
    }

    @Test
    void testUnloadDropsThePartition() {
        val stored = item(10);
        val cache = loaded(EAST, List.of(stored));

        assertTrue(cache.unload(EAST).join().isSuccess());

        assertTrue(cache.find(EAST, stored.id()).isFailure());
        assertTrue(cache.list(EAST).get().isEmpty());

        // Second unload has no partition to close, and that is not an error.
        assertTrue(cache.unload(EAST).join().isSuccess());
    }

    @Test
    void testPartitionsDoNotSeeEachOther() {
        val mine = item(10);
        val yours = item(20);
        val cache = new ItemCache(Map.of(EAST, List.of(mine), WEST, List.of(yours)));
        assertTrue(cache.load(EAST).join().isSuccess());
        assertTrue(cache.load(WEST).join().isSuccess());

        assertEquals(List.of(mine), cache.list(EAST).get());
        assertEquals(List.of(yours), cache.list(WEST).get());
        assertTrue(cache.find(EAST, yours.id()).isFailure());

        cache.unload(EAST).join();
        cache.unload(WEST).join();
    }

    private static <T> String failureOf(Result<T, String> result) {
        if (result instanceof Result.Failure<T, String>(var error)) return error;
        throw new AssertionError("esperava falha, veio " + result);
    }
}
