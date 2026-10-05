package br.commons.serializer;

import br.commons.Logger;
import br.commons.Strings;
import br.commons.Tuple;
import br.commons.serializer.document.builder.DocumentBuilder;
import br.commons.serializer.document.Overlay;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

/**
 * The only API for reading a document, regardless of source format ({@link Json}, {@link Yaml}):
 * query by dotted path ({@code server.ports.0.number}), scalar coercion, structure
 * ({@link #subtree}, {@link #list}) and binding to a type — typically a {@code record}.
 *
 * <p>Behind it is always the common internal representation, {@code br.commons.serializer.document.Document}: formats
 * only read and validate input. Typed values and sub-documents are cached in an LRU cache with time window
 * ({@code br.commons.serializer.cache.ttl.seconds}, {@code br.commons.serializer.cache.size}; read at startup).
 *
 * <p>Creation and editing: {@link #builder()} and {@link #builder(Serializer)}; persistence: {@link Yaml#save}.
 */
@NullMarked
public interface Serializer {

    /** New empty document. */
    static DocumentBuilder builder() { return DocumentBuilder.create(); }

    /** Editing {@code base}: mutable copy, same source; {@code base} does not change. */
    static DocumentBuilder builder(Serializer base) { return DocumentBuilder.edit(base); }

    /** Scalars from {@code overrides} (system properties, test map) on top of {@code base}. */
    static Serializer overlay(Serializer base, Function<String, @Nullable String> overrides) { return new Overlay(base, overrides); }

    /** Document with no content: missing, empty map, or empty list. */
    boolean isEmpty();

    /** Whether the dotted path exists in the document. */
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

    /** Lists of scalars written as text ({@code "1, 2, 3"}). */
    default byte[]   asBytes   (String key) { return Strings.bytes(asString(key)); }
    default short[]  asShorts  (String key) { return Strings.shorts(asString(key)); }
    default int[]    asInts    (String key) { return Strings.ints(asString(key)); }
    default long[]   asLongs   (String key) { return Strings.longs(asString(key)); }
    default float[]  asFloats  (String key) { return Strings.floats(asString(key)); }
    default double[] asDoubles (String key) { return Strings.doubles(asString(key)); }

    /** The constant whose name is the key text; {@code null} if the key is missing or does not name any constant. */
    default <T extends Enum<T>> @Nullable T asEnum(String key, Class<T> type) {
        val value = asString(key);
        if (value == null) return null;
        for (val constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) return constant;
        }
        return null;
    }

    /** Pairs {@code name}/{@code value} from the list at {@code key}; duplicate names keep the first. */
    default Tuple[] asTuples(String key) {
        val properties = new HashMap<String, String>();
        if (contains(key)) {
            for (val property : list(key)) {
                val name = property.asString("name");
                if (name == null) continue;

                val value = property.asString("value");
                if (value == null) continue;

                if (properties.containsKey(name)) {
                    Logger.warn("Duplicate property `" + name + "` in `" + key + "`");
                    continue;
                }
                properties.put(name, value);
            }
        }
        return properties.entrySet().stream()
                .map(v -> new Tuple(v.getKey(), v.getValue()))
                .toArray(Tuple[]::new);
    }

    /** Materializes the entire document to the requested type — typically a {@code record}. */
    <T> T as(Class<T> type);

    /** Materializes a document that is a top-level list; empty list when it is not. */
    <T> List<T> asList(Class<T> type);

    /** The map (or list, under its own name) at the path, as a document; {@code null} if missing. */
    @Nullable Serializer subtree(String key);

    /** Each item from the list at the path, as a document; empty when the path is not a list. */
    List<Serializer> list(String key);

    /** The directory of the source file; {@code null} if the document did not come from a file. */
    @Nullable Path folder();
}
