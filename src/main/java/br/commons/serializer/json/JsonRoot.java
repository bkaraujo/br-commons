package br.commons.serializer.json;

import br.commons.Logger;
import br.commons.serializer.Json;
import br.commons.serializer.core.Scalars;
import br.commons.serializer.core.TreeBinder;
import br.commons.serializer.core.Trees;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Implementação de {@link Json}: guarda a árvore genérica que o {@link JsonParser} produziu e
 * delega navegação, coerção e binding para {@code br.commons.serializer.core}. A sintaxe de saída
 * é do {@link JsonWriter}.
 */
@NullMarked
public final class JsonRoot implements Json {

    private final @Nullable Object tree;

    private JsonRoot(@Nullable Object tree) {
        this.tree = tree;
    }

    public static JsonRoot from(String text) {
        return new JsonRoot(new JsonParser(text).parseValue());
    }

    public static String write(@Nullable Object value) {
        return JsonWriter.write(value);
    }

    @Override
    public boolean isEmpty() {
        return switch (tree) {
            case null -> true;
            case Map<?, ?> map -> map.isEmpty();
            case List<?> list -> list.isEmpty();
            default -> false;
        };
    }

    @Override
    public boolean contains(String key) {
        return find(key) != null;
    }

    @Override @Nullable public String  asString  (String key) { return as(key, String.class); }
    @Override @Nullable public Integer asInt     (String key) { return as(key, Integer.class); }
    @Override @Nullable public Long    asLong    (String key) { return as(key, Long.class); }
    @Override @Nullable public Double  asDouble  (String key) { return as(key, Double.class); }
    @Override @Nullable public Boolean asBoolean (String key) { return as(key, Boolean.class); }

    @Override
    public <T> T as(Class<T> type) {
        return TreeBinder.bind(tree, type);
    }

    @Override
    public <T> List<T> asList(Class<T> type) {
        if (!(tree instanceof List<?> items)) {
            Logger.warn("Document is not a json array");
            return List.of();
        }
        return items.stream().map(item -> TreeBinder.bind(item, type)).toList();
    }

    @Nullable
    private <T> T as(String key, Class<T> type) {
        val found = find(key);
        return found == null ? null : Scalars.coerce(found, type);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Object find(String key) {
        if (!(tree instanceof Map<?, ?> map)) return null;
        return Trees.find((Map<String, Object>) map, key);
    }
}
