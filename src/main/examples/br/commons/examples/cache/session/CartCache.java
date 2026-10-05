package br.commons.examples.cache.session;

import br.commons.cache.AbstractCache;
import br.commons.platform.NativeCache;
import org.jspecify.annotations.NullMarked;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Cart lines off-heap, one partition per user. Layout: id (16) + quantity (8) + priceCents (8) — {@code SIZE},
 * the writer and {@link #mapToDomain} change together.
 */
@NullMarked
public final class CartCache extends AbstractCache<CartItem> {
    private static final long SIZE = 32;

    /** @param loader reads a user's cart from the source of truth (database, service...) when the session opens */
    public CartCache(Function<String, List<CartItem>> loader) {
        super("CART:",
                loader,
                CartItem::id,
                _ -> SIZE,
                (segment, item) -> {
                    NativeCache.writeUuid(segment, 0, item.id());
                    segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 16, item.quantity());
                    segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 24, item.priceCents());
                });
    }

    @Override
    protected CartItem mapToDomain(MemorySegment segment) {
        return new CartItem(
                Objects.requireNonNull(NativeCache.readUuid(segment, 0)),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 16),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 24));
    }
}
