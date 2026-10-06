package br.commons.examples.cache.session;

import br.commons.cache.CacheRegion;
import br.commons.platform.NativeCache;
import org.jspecify.annotations.NullMarked;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cart lines off-heap, one partition per user. Layout: id (16) + quantity (8) + priceCents (8) — {@code SIZE},
 * {@link #write} and {@link #read} change together.
 *
 * @param source reads a user's cart from the source of truth (database, service...) when the session opens
 */
@NullMarked
public record CartRegion(Function<String, List<CartItem>> source) implements CacheRegion<CartItem> {
    private static final long SIZE = 32;

    @Override
    public String name() {
        return "CART";
    }

    @Override
    public List<CartItem> load(String userId) {
        return source.apply(userId);
    }

    @Override
    public UUID key(CartItem item) {
        return item.id();
    }

    @Override
    public long size(CartItem item) {
        return SIZE;
    }

    @Override
    public void write(MemorySegment segment, CartItem item) {
        NativeCache.writeUuid(segment, 0, item.id());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 16, item.quantity());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 24, item.priceCents());
    }

    @Override
    public CartItem read(MemorySegment segment) {
        return new CartItem(
                Objects.requireNonNull(NativeCache.readUuid(segment, 0)),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 16),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 24));
    }
}
