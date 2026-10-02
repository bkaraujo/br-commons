package br.commons.serializer;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * O que todo documento já parseado oferece, qualquer que seja o formato ({@link Json}, {@link Yaml}): consulta por
 * caminho pontuado ({@code servidor.portas.0.numero}), coerção dos escalares e binding do documento inteiro para um
 * tipo — tipicamente um {@code record}. Cada formato acrescenta o que é só seu.
 *
 * <p>As implementações sobre a árvore genérica partem de {@code br.commons.serializer.core.TreeSerializer}.
 */
@NullMarked
public interface Serializer {

    /** Documento sem conteúdo: ausente, mapa vazio ou lista vazia. */
    boolean isEmpty();

    /** Se o caminho pontuado existe no documento. */
    boolean contains(String key);

    @Nullable String  asString  (String key);
    @Nullable Byte    asByte    (String key);
    @Nullable Short   asShort   (String key);
    @Nullable Integer asInt     (String key);
    @Nullable Long    asLong    (String key);
    @Nullable Float   asFloat   (String key);
    @Nullable Double  asDouble  (String key);
    @Nullable Boolean asBoolean (String key);

    default Byte    asByte    (String key, byte    defaultValue) { Byte    v = asByte(key);    return v == null ? defaultValue : v; }
    default Short   asShort   (String key, short   defaultValue) { Short   v = asShort(key);   return v == null ? defaultValue : v; }
    default Integer asInt     (String key, int     defaultValue) { Integer v = asInt(key);     return v == null ? defaultValue : v; }
    default Long    asLong    (String key, long    defaultValue) { Long    v = asLong(key);    return v == null ? defaultValue : v; }
    default Boolean asBoolean (String key, boolean defaultValue) { Boolean v = asBoolean(key); return v == null ? defaultValue : v; }
    default Double  asDouble  (String key, double  defaultValue) { Double  v = asDouble(key);  return v == null ? defaultValue : v; }
    default String  asString  (String key, String  defaultValue) { String  v = asString(key);  return v == null ? defaultValue : v; }
    default Float   asFloat   (String key, float   defaultValue) { Float   v = asFloat(key);   return v == null ? defaultValue : v; }

    /** A constante cujo nome é o texto da chave; {@code null} quando a chave falta ou não nomeia nenhuma. */
    default <T extends Enum<T>> @Nullable T asEnum(String key, Class<T> type) {
        val value = asString(key);
        if (value == null) return null;
        for (val constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) return constant;
        }
        return null;
    }

    /** Materializa o documento inteiro no tipo pedido — tipicamente um {@code record}. */
    <T> T as(Class<T> type);
}
