package br.commons.serializer.core;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Coerção do escalar cru da árvore ({@link String}, {@link Number}, {@link Boolean}) para o tipo
 * pedido. É o outro pedaço comum entre YAML e JSON: os dois leem o mesmo punhado de tipos crus e
 * precisam entregar {@code int}, {@code UUID}, enum, {@code boolean}.
 *
 * <p>Devolve {@code null} quando o tipo alvo não é suportado e deixa a {@link NumberFormatException}
 * propagar quando o texto não é número: a política de falha — log fatal, exceção de fronteira,
 * valor default — é de quem chama, nunca daqui.
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

    /** {@code null} quando {@code type} não é um escalar suportado. */
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
