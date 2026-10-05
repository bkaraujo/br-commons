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

    /** Regression (C9): a read racing an unload touched the closed arena and threw IllegalStateException. */
    @Test
    void readsRacingUnloadNeverThrow() throws Exception {
        val items = new java.util.ArrayList<Item>();
        for (int i = 0; i < 200; i++) items.add(item(i));
        val probe = items.get(100);
        val cache = new ItemCache(Map.of(EAST, items));
        val stop = new java.util.concurrent.atomic.AtomicBoolean();
        val errors = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();

        val readers = new java.util.ArrayList<Thread>();
        for (int r = 0; r < 4; r++) {
            readers.add(Thread.ofPlatform().start(() -> {
                while (!stop.get()) {
                    try {
                        cache.list(EAST);
                        cache.find(EAST, probe.id());
                    } catch (Throwable e) {
                        errors.add(e);
                        return;
                    }
                }
            }));
        }
        for (int cycle = 0; cycle < 50; cycle++) {
            cache.load(EAST).join();
            cache.unload(EAST).join();
        }
        stop.set(true);
        for (val reader : readers) reader.join(5_000);

        assertEquals(List.of(), errors);
    }

    @SuppressWarnings("unchecked")
    private static NativeCache<UUID> nativeOf(ItemCache cache, String partition) throws Exception {
        val field = AbstractCache.class.getDeclaredField("registry");
        field.setAccessible(true);
        val registry = (Map<String, java.util.concurrent.CompletableFuture<NativeCache<UUID>>>) field.get(cache);
        return registry.get(partition).join();
    }

    /** Regression (C6): loading a partition again replaced its cache without closing it (native memory leak). */
    @Test
    void reloadClosesThePreviousCache() throws Exception {
        val stored = item(10);
        val cache = loaded(EAST, List.of(stored));
        val first = nativeOf(cache, EAST);

        assertTrue(cache.load(EAST).join().isSuccess());
        assertTrue(first.isClosed());
        assertEquals(stored, cache.find(EAST, stored.id()).get());

        cache.unload(EAST).join();
    }

    /** Regression (C7): repeated upserts of the same record grew the partition's native memory without bound. */
    @Test
    void repeatedUpsertsAreCompacted() throws Exception {
        val stored = item(10);
        val cache = loaded(EAST, List.of(stored));
        for (int i = 0; i < 2_000; i++) {
            assertTrue(cache.upsert(EAST, new Item(stored.id(), i)).join().isSuccess());
        }
        assertTrue(nativeOf(cache, EAST).allocatedBytes() <= 4 * SIZE, "allocated " + nativeOf(cache, EAST).allocatedBytes());
        assertEquals(new Item(stored.id(), 1_999), cache.find(EAST, stored.id()).get());

        cache.unload(EAST).join();
    }

    /** Plan C9 × C7: reads racing compactions (old arena closed after the grace) never report a false miss. */
    @Test
    void readsRacingCompactionNeverMiss() throws Exception {
        val probe = item(1);
        val hot = item(2);
        val cache = loaded(EAST, List.of(probe, hot));
        val stop = new java.util.concurrent.atomic.AtomicBoolean();
        val misses = new java.util.concurrent.atomic.AtomicInteger();
        val errors = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        val reader = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                try {
                    if (cache.find(EAST, probe.id()).isFailure()) misses.incrementAndGet();
                } catch (Throwable e) {
                    errors.add(e);
                    return;
                }
            }
        });
        val deadline = System.nanoTime() + java.time.Duration.ofMillis(2_500).toNanos();
        for (int i = 0; System.nanoTime() < deadline; i++) {
            cache.upsert(EAST, new Item(hot.id(), i)).join();
        }
        stop.set(true);
        reader.join(5_000);

        assertEquals(List.of(), errors);
        assertEquals(0, misses.get());
        cache.unload(EAST).join();
    }
}
