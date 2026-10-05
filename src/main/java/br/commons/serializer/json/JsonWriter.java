package br.commons.serializer.json;

import br.commons.serializer.Serializer;
import br.commons.serializer.document.Document;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Output syntax: Java object to JSON text. No third-party databind. */
@NullMarked
public final class JsonWriter {

    private static final Map<Character, String> ESCAPES = Map.of(
            '"', "\\\"", '\\', "\\\\", '\n', "\\n", '\r', "\\r", '\t', "\\t");

    private JsonWriter() {}

    public static String write(@Nullable Object value) {
        val sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, @Nullable Object value) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> writeString(sb, s);
            case UUID id -> writeString(sb, id.toString());
            case Enum<?> e -> writeString(sb, e.name());
            case Boolean b -> sb.append(b);
            case Number n -> sb.append(n);
            case Serializer document -> writeValue(sb, Document.unwrap(document).tree());
            case Map<?, ?> map -> writeMap(sb, map);
            case Iterable<?> it -> writeArray(sb, it);
            case Object[] arr -> writeArray(sb, List.of(arr));
            default -> writeRecordOrFallback(sb, value);
        }
    }

    private static void writeRecordOrFallback(StringBuilder sb, Object value) {
        val klass = value.getClass();
        if (klass.isRecord()) {
            writeRecord(sb, value, klass);
            return;
        }
        writeString(sb, value.toString());
    }

    private static void writeRecord(StringBuilder sb, Object value, Class<?> klass) {
        val components = klass.getRecordComponents();
        sb.append('{');
        for (int i = 0; i < components.length; i++) {
            if (i > 0) sb.append(',');
            writeString(sb, components[i].getName());
            sb.append(':');
            try {
                // setAccessible: non-public record (nested, package-private) also serializes.
                val accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                writeValue(sb, accessor.invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to serialize " + klass, e);
            }
        }
        sb.append('}');
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (val entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> it) {
        sb.append('[');
        boolean first = true;
        for (Object item : it) {
            if (!first) sb.append(',');
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            appendEscaped(sb, s.charAt(i));
        }
        sb.append('"');
    }

    private static void appendEscaped(StringBuilder sb, char c) {
        val escape = ESCAPES.get(c);
        if (escape != null) {
            sb.append(escape);
        } else if (c < 0x20) {
            sb.append("\\u%04x".formatted((int) c));
        } else {
            sb.append(c);
        }
    }
}
