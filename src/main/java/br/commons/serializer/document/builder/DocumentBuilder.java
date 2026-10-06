package br.commons.serializer.document.builder;

import br.commons.serializer.Serializer;
import br.commons.serializer.document.Document;
import br.commons.serializer.document.Policy;
import br.commons.serializer.document.navigation.Trees;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates or edits a document without knowing format: works on a mutable copy of the tree and delivers a new
 * immutable {@link Document} on each {@link #build()}. Persistence is format-specific ({@code Yaml.save}).
 *
 * <p>Keys are dotted paths ({@code server.port}); missing intermediate maps are created.
 */
@NullMarked
public final class DocumentBuilder {

    private final Map<String, Object> raw;
    private final @Nullable Path source;
    private final Policy policy;

    private DocumentBuilder(Map<String, Object> raw, @Nullable Path source, Policy policy) {
        this.raw = raw;
        this.source = source;
        this.policy = policy;
    }

    /** New document, no source, with configuration policy. */
    public static DocumentBuilder create() {
        return new DocumentBuilder(new LinkedHashMap<>(), null, Policy.CONFIG);
    }

    /** Editing an existing document: same source and policy; the original does not change. */
    public static DocumentBuilder edit(Serializer base) {
        val document = Document.unwrap(base);
        val copy = document.tree() instanceof Map<?, ?> map
                ? mutableMap(map)
                : new LinkedHashMap<String, Object>();
        return new DocumentBuilder(copy, document.source(), document.policy());
    }

    public Document build() {
        return Document.of(raw, source, policy);
    }

    public DocumentBuilder clear() {
        raw.clear();
        return this;
    }

    public DocumentBuilder append(String key, String value) {
        return append(new Entry(key, value));
    }

    public DocumentBuilder append(Entry... values) {
        for (val entry : values) {
            raw.putAll(entry.toMap());
        }
        return this;
    }

    public DocumentBuilder append(String key, String name, Object value) {
        return append(key, new Entry(name, value));
    }

    /** Appends each entry as an item to the list at {@code key}, creating the list if missing. */
    @SuppressWarnings("unchecked")
    public DocumentBuilder append(String key, Entry... values) {
        val items = new ArrayList<Object>();
        for (val entry : values) {
            items.add(entry.toMap());
        }

        if (Trees.find(raw, key) instanceof List<?> existing) {
            ((List<Object>) existing).addAll(items);
            return this;
        }

        val dotIndex = key.lastIndexOf('.');
        if (dotIndex > 0) {
            newMap(key.substring(0, dotIndex)).put(key.substring(dotIndex + 1), items);
        } else {
            raw.put(key, items);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public DocumentBuilder put(String key, Object value) {
        val copy = value instanceof Map<?, ?> || value instanceof List<?> ? mutableCopy(value) : value;

        val dotIndex = key.lastIndexOf('.');
        if (dotIndex < 0) {
            raw.put(key, copy);
            return this;
        }

        val path = key.substring(0, dotIndex);
        val item = key.substring(dotIndex + 1);
        var container = Trees.find(raw, path);
        if (!(container instanceof Map<?, ?>)) {
            container = newMap(path);
        }
        ((Map<String, Object>) container).put(item, copy);
        return this;
    }

    // ========== Internal Methods ==========

    /** The map at the path, creating missing ones; a segment that lands on a list appends a new map to it. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> newMap(String path) {
        Object container = raw;
        for (val token : Trees.splitKey(path)) {
            if (container instanceof Map<?, ?> m) {
                val map = (Map<String, Object>) m;
                var next = map.get(token);
                if (!(next instanceof Map<?, ?>) && !(next instanceof List<?>)) {
                    next = new LinkedHashMap<String, Object>();
                    map.put(token, next);
                }
                container = next;
            } else if (container instanceof List<?> l) {
                val created = new LinkedHashMap<String, Object>();
                ((List<Object>) l).add(created);
                container = created;
            }
        }

        if (container instanceof List<?> l) {
            val created = new LinkedHashMap<String, Object>();
            ((List<Object>) l).add(created);
            return created;
        }
        return (Map<String, Object>) container;
    }

    private static Map<String, Object> mutableMap(Map<?, ?> map) {
        val copy = new LinkedHashMap<String, Object>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), mutableCopy(v)));
        return copy;
    }

    private static @Nullable Object mutableCopy(@Nullable Object node) {
        if (node instanceof Map<?, ?> map) return mutableMap(map);
        if (node instanceof List<?> list) {
            val copy = new ArrayList<Object>(list.size());
            list.forEach(item -> copy.add(mutableCopy(item)));
            return copy;
        }
        return node;
    }
}
