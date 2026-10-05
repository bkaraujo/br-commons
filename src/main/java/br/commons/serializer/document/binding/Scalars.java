package br.commons.serializer.document.binding;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Coercion of raw scalar from the tree ({@link String}, {@link Number}, {@link Boolean}) to the requested type.
 * It is the other piece common to YAML and JSON: both read the same set of raw types and
 * must deliver {@code int}, {@code UUID}, enum, {@code boolean}.
 *
 * <p>Returns {@code null} when the target type is not supported and lets {@link NumberFormatException}
 * propagate when text is not a number: the failure policy — log fatal, boundary exception,
 * default value — is the caller's responsibility, never this class's.
 */
@NullMarked
public abstract class Scalars {

    private record Numeric(Function<Number, Number> fromNumber, Function<String, Number> fromText) {}

    private static final Map<Class<?>, Numeric> NUMERICS = Map.ofEntries(
            Map.entry(byte.class,    new Numeric(Number::byteValue,   Byte::valueOf)),
            Map.entry(Byte.class,    new Numeric(Number::byteValue,   Byte::valueOf)),
            Map.entry(short.class,   new Numeric(Number::shortValue,  Short::valueOf)),
            Map.entry(Short.class,   new Numeric(Number::shortValue,  Short::valueOf)),
            Map.entry(int.class,     new Numeric(Number::intValue,    Integer::valueOf)),
            Map.entry(Integer.class, new Numeric(Number::intValue,    Integer::valueOf)),
            Map.entry(long.class,    new Numeric(Number::longValue,   Long::valueOf)),
            Map.entry(Long.class,    new Numeric(Number::longValue,   Long::valueOf)),
            Map.entry(float.class,   new Numeric(Number::floatValue,  Float::valueOf)),
            Map.entry(Float.class,   new Numeric(Number::floatValue,  Float::valueOf)),
            Map.entry(double.class,  new Numeric(Number::doubleValue, Double::valueOf)),
            Map.entry(Double.class,  new Numeric(Number::doubleValue, Double::valueOf)));

    private Scalars() {}

    /** {@code null} when {@code type} is not a supported scalar. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T coerce(Object value, Class<T> type) {
        return (T) raw(value, type);
    }

    @Nullable
    private static Object raw(Object value, Class<?> type) {
        if (type.isInstance(value)) return value;
        if (type == Object.class) return value;
        if (type == String.class) return value.toString();
        if (isBoolean(type)) return asBoolean(value);

        val number = asNumber(value, type);
        return number != null ? number : fromText(value, type);
    }

    private static boolean isBoolean(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    private static Object asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(value.toString());
    }

    @Nullable
    private static Object asNumber(Object value, Class<?> type) {
        val numeric = NUMERICS.get(type);
        if (numeric == null) return null;
        if (value instanceof Number number) return numeric.fromNumber().apply(number);
        return numeric.fromText().apply(value.toString());
    }

    @Nullable
    private static Object fromText(Object value, Class<?> type) {
        if (type == UUID.class) return UUID.fromString(value.toString());
        if (type.isEnum()) return Enum.valueOf(type.asSubclass(Enum.class), value.toString());
        return null;
    }
}
