package br.commons.serializer.json;

import br.commons.Logger;
import br.commons.serializer.Json;
import br.commons.serializer.core.TreeBinder;
import br.commons.serializer.core.TreeSerializer;
import br.commons.serializer.core.Trees;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Implementação de {@link Json}: guarda a árvore genérica que o {@link JsonParser} produziu. Consulta, escalares e
 * binding são do {@link TreeSerializer}; a sintaxe de saída é do {@link JsonWriter}.
 */
@NullMarked
public final class JsonRoot extends TreeSerializer implements Json {

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
    protected @Nullable Object tree() {
        return tree;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected @Nullable Object find(String key) {
        if (!(tree instanceof Map<?, ?> map)) return null;
        return Trees.find((Map<String, Object>) map, key);
    }

    @Override
    public <T> List<T> asList(Class<T> type) {
        if (!(tree instanceof List<?> items)) {
            Logger.warn("Document is not a json array");
            return List.of();
        }
        return items.stream().map(item -> TreeBinder.bind(item, type)).toList();
    }
}
