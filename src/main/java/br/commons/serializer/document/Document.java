package br.commons.serializer.document;

import br.commons.Logger;
import br.commons.locale.i18n.Errors;
import br.commons.serializer.Serializer;
import br.commons.serializer.document.binding.Scalars;
import br.commons.serializer.document.binding.TreeBinder;
import br.commons.serializer.document.builder.DocumentBuilder;
import br.commons.serializer.document.cache.CacheSettings;
import br.commons.serializer.document.cache.LruCache;
import br.commons.serializer.document.navigation.Trees;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * The internal representation of every document read, regardless of format: an immutable tree of
 * {@link Map} ({@code String} → value), {@link List}, and scalars ({@link String}, {@link Number},
 * {@link Boolean}, {@code null}). Formats ({@code json}, {@code yaml}) only read and validate input and
 * arrive here via {@link #of}; {@link DocumentBuilder} creates and edits; reading is via {@link Serializer}.
 *
 * <p>Hotpath: each typed value (and each sub-document) is stored in an {@link LruCache} by
 * {@code (path, type)}, with window and size from {@link CacheSettings}. Immutability is what allows
 * caching without invalidation and sharing the same instance across threads ({@link DocumentCache}).
 */
@NullMarked
public final class Document implements Serializer {

    private record Key(String path, Class<?> type) {}

    private final @Nullable Object tree;
    private final @Nullable Path source;
    private final Policy policy;
    private final LruCache<Key, Object> values = LruCache.standard();

    /** Flat index for O(1) lookup, built on first use; benign race — result is always the same. */
    private volatile @Nullable Map<String, Object> flat;

    private Document(@Nullable Object tree, @Nullable Path source, Policy policy) {
        this.tree = tree;
        this.source = source;
        this.policy = policy;
    }

    /**
     * Validates and copies the raw tree from a reader to the internal representation: map keys must be text
     * ({@link IllegalArgumentException} if not); enum becomes its name and unknown scalar becomes text.
     */
    public static Document of(@Nullable Object tree, @Nullable Path source, Policy policy) {
        return new Document(normalize(tree), source, policy);
    }

    public static Document empty(@Nullable Path source, Policy policy) {
        return new Document(Map.of(), source, policy);
    }

    /** The {@link Document} behind a {@link Serializer} — traverses {@link Overlay}, which only modifies scalars. */
    public static Document unwrap(Serializer serializer) {
        return switch (serializer) {
            case Document document -> document;
            case Overlay overlay -> unwrap(overlay.base());
            default -> throw new IllegalArgumentException(Errors.get().unsupportedSerializer(serializer.getClass()).value());
        };
    }

    /** The entire tree, immutable — for those who persist or edit, not for reading. */
    public @Nullable Object tree() {
        return tree;
    }

    /** The file the document came from; {@code null} if it did not come from a file. */
    public @Nullable Path source() {
        return source;
    }

    public Policy policy() {
        return policy;
    }

    // ========== Serializer ==========

    @Override
    public boolean isEmpty() {
        return switch (tree) {
            case null -> true;
            case Map<?, ?> map -> map.isEmpty();
            case List<?> list -> list.isEmpty();
            default -> false;
        };
    }

    /** A key whose value is {@code null} in the document still exists. */
    @Override
    public boolean contains(String key) {
        val index = flat();
        return index != null && (index.containsKey(key) || find(key) != null);
    }

    @Override @Nullable public String  asString  (String key) { return value(key, String.class); }
    @Override @Nullable public Byte    asByte    (String key) { return value(key, Byte.class); }
    @Override @Nullable public Short   asShort   (String key) { return value(key, Short.class); }
    @Override @Nullable public Integer asInt     (String key) { return value(key, Integer.class); }
    @Override @Nullable public Long    asLong    (String key) { return value(key, Long.class); }
    @Override @Nullable public Float   asFloat   (String key) { return value(key, Float.class); }
    @Override @Nullable public Double  asDouble  (String key) { return value(key, Double.class); }
    @Override @Nullable public Boolean asBoolean (String key) { return value(key, Boolean.class); }

    @Override
    public <T> T as(Class<T> type) {
        return TreeBinder.bind(tree, type);
    }

    @Override
    public <T> List<T> asList(Class<T> type) {
        if (!(tree instanceof List<?> items)) {
            Logger.warn("Document is not a list");
            return List.of();
        }
        return items.stream().map(item -> TreeBinder.bind(item, type)).toList();
    }

    @Override
    public @Nullable Serializer subtree(String key) {
        val cacheKey = new Key(key, Serializer.class);
        val cached = values.lookup(cacheKey);
        if (cached != LruCache.MISS) return (Serializer) cached;

        val sub = buildSubtree(key);
        values.put(cacheKey, sub);
        return sub;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Serializer> list(String key) {
        val cacheKey = new Key(key, List.class);
        val cached = values.lookup(cacheKey);
        if (cached != LruCache.MISS) return (List<Serializer>) Objects.requireNonNull(cached);

        val items = buildList(key);
        values.put(cacheKey, items);
        return items;
    }

    @Override
    public @Nullable Path folder() {
        return source != null ? source.getParent() : null;
    }

    @Override
    public String toString() {
        return source != null ? source.toString() : String.valueOf(tree);
    }

    // ========== Lookup ==========

    /** The raw value at the dotted path: flat index first (O(1)), tree when not a leaf. */
    private @Nullable Object find(String key) {
        val index = flat();
        if (index == null) return null;

        val value = index.get(key);
        if (value != null || index.containsKey(key)) return value;
        return Trees.find(root(), key);
    }

    private <T> @Nullable T value(String key, Class<T> type) {
        val cacheKey = new Key(key, type);
        val cached = values.lookup(cacheKey);
        if (cached != LruCache.MISS) return type.cast(cached);

        val found = find(key);
        val result = found == null ? null : convert(found, type);
        values.put(cacheKey, result);
        return result;
    }

    private <T> @Nullable T convert(Object value, Class<T> type) {
        if (policy == Policy.WIRE) return Scalars.coerce(value, type);

        try {
            val coerced = Scalars.coerce(value, type);
            val result = coerced != null ? coerced : type.cast(value);
            return result instanceof String text ? type.cast(text.trim()) : result;
        } catch (NumberFormatException e) {
            Logger.fatal("Expected a number, got %s", value);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> root() {
        return (Map<String, Object>) Objects.requireNonNull(tree);
    }

    private @Nullable Map<String, Object> flat() {
        if (!(tree instanceof Map<?, ?>)) return null;

        var index = flat;
        if (index == null) {
            index = Trees.flatten(root());
            flat = index;
        }
        return index;
    }

    private @Nullable Serializer buildSubtree(String key) {
        if (!(tree instanceof Map<?, ?>)) return null;

        val found = Trees.find(root(), key);
        if (found instanceof Map<?, ?>) return new Document(found, source, policy);

        if (found instanceof List<?>) {
            val dotIndex = key.lastIndexOf('.');
            val name = dotIndex > 0 ? key.substring(dotIndex + 1) : key;
            return new Document(Collections.singletonMap(name, found), source, policy);
        }

        return null;
    }

    private List<Serializer> buildList(String key) {
        val found = tree instanceof Map<?, ?> ? Trees.find(root(), key) : null;
        if (!(found instanceof List<?> entries)) {
            Logger.warn("Key must relate to a list: %s", key);
            return List.of();
        }

        val result = new ArrayList<Serializer>(entries.size());
        for (val entry : entries) {
            result.add(new Document(entry, source, policy));
        }
        return Collections.unmodifiableList(result);
    }

    // ========== Validation ==========

    private static @Nullable Object normalize(@Nullable Object node) {
        return switch (node) {
            case null -> null;
            case String text -> text;
            case Number number -> number;
            case Boolean bool -> bool;
            case Map<?, ?> map -> normalizeMap(map);
            case List<?> list -> normalizeList(list);
            case Enum<?> constant -> constant.name();
            default -> node.toString();
        };
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> map) {
        val copy = new LinkedHashMap<String, Object>(map.size() * 2);
        for (val entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(Errors.get().keyMustBeText(String.valueOf(entry.getKey())).value());
            }
            copy.put(key, normalize(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<Object> normalizeList(List<?> list) {
        val copy = new ArrayList<Object>(list.size());
        for (val item : list) {
            copy.add(normalize(item));
        }
        return Collections.unmodifiableList(copy);
    }
}
