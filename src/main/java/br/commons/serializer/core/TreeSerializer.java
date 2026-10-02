package br.commons.serializer.core;

import br.commons.serializer.Serializer;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * O {@link Serializer} sobre a árvore genérica que todo formato produz depois de parseado ({@link Trees}): o que o
 * JSON e o YAML faziam igual — os acessores escalares, {@link #isEmpty()}, {@link #contains(String)} e o binding
 * ({@link TreeBinder}) — fica aqui, sobre dois ganchos: {@link #tree()} e {@link #find(String)}.
 *
 * <p>A coerção de um valor achado é de {@link Scalars}; um formato com política própria (cache, {@code trim},
 * falha fatal) sobrescreve {@link #value(String, Class)}.
 */
@NullMarked
public abstract class TreeSerializer implements Serializer {

    /** O documento inteiro: mapa, lista, escalar ou {@code null}. */
    protected abstract @Nullable Object tree();

    /** O valor do caminho pontuado, sem coerção; {@code null} quando falta. */
    protected abstract @Nullable Object find(String key);

    /** O valor do caminho no tipo pedido; {@code null} quando falta ou não converte. */
    protected <T> @Nullable T value(String key, Class<T> type) {
        val found = find(key);
        return found == null ? null : Scalars.coerce(found, type);
    }

    @Override
    public boolean isEmpty() {
        return switch (tree()) {
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
        return TreeBinder.bind(tree(), type);
    }
}
