package br.commons.lang.xml;

import br.commons.Logger;
import br.commons.locale.i18n.Errors;
import br.commons.serializer.Serializer;
import br.commons.serializer.document.Document;
import br.commons.serializer.document.DocumentCache;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Output syntax: Java object to XML text, the inverse of {@link XmlReader}. Map keys and record components become
 * child elements (never attributes); a list under a key repeats the element; a list anywhere else becomes
 * {@value #ITEM} children; {@code null} is an empty element. Persistence of any document — read from any format or
 * created by {@link Serializer#builder} — keeps the root name of the existing file and evicts it from the cache of
 * {@link DocumentCache}: the next read sees what was saved.
 */
@NullMarked
public final class XmlWriter {

    /** Root element when none is given and the file has none. */
    public static final String ROOT = "root";

    /** Element of each item of a list that is not under a key. */
    public static final String ITEM = "item";

    private static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String INDENT = "  ";

    /** {@code \r} as a reference: a literal one would be normalized to {@code \n} on reading. */
    private static final Map<Character, String> ESCAPES = Map.of('&', "&amp;", '<', "&lt;", '>', "&gt;", '\r', "&#13;");

    private XmlWriter() {}

    public static String write(@Nullable Object value, String root) {
        val sb = new StringBuilder();
        element(sb, root, value, "", 0);
        return sb.toString();
    }

    public static void save(Serializer document) {
        val source = Document.unwrap(document).source();
        if (source == null) {
            Logger.warn("Failed to save: document not loaded from a known file");
            return;
        }
        save(document, source);
    }

    public static void save(Serializer document, Path path) {
        try {
            val existing = XmlReader.rootName(path);
            val sb = new StringBuilder(DECLARATION);
            element(sb, existing != null ? existing : ROOT, Document.unwrap(document).tree(), INDENT, 0);
            sb.append('\n');
            Logger.debug("Writing %s", path);
            Files.writeString(path, sb, StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            Logger.error("Failed to write %s: %s", path, throwable);
        } finally {
            DocumentCache.invalidate(path);
        }
    }

    /** Child element: name and value, {@code null} included. */
    private record Child(String name, @Nullable Object value) {}

    private static void element(StringBuilder sb, String name, @Nullable Object value, String indent, int depth) {
        checkName(name);
        switch (value) {
            case null -> sb.append('<').append(name).append("/>");
            case Serializer document -> element(sb, name, Document.unwrap(document).tree(), indent, depth);
            case Map<?, ?> map -> children(sb, name, fields(map), indent, depth);
            case Iterable<?> it -> children(sb, name, items(it), indent, depth);
            case Object[] arr -> children(sb, name, items(Arrays.asList(arr)), indent, depth);
            default -> {
                val klass = value.getClass();
                if (klass.isRecord()) {
                    children(sb, name, components(value, klass), indent, depth);
                } else {
                    sb.append('<').append(name).append('>');
                    appendText(sb, text(value));
                    sb.append("</").append(name).append('>');
                }
            }
        }
    }

    private static void children(StringBuilder sb, String name, List<Child> children, String indent, int depth) {
        if (children.isEmpty()) {
            sb.append('<').append(name).append("/>");
            return;
        }
        sb.append('<').append(name).append('>');
        for (val child : children) {
            newLine(sb, indent, depth + 1);
            element(sb, child.name(), child.value(), indent, depth + 1);
        }
        newLine(sb, indent, depth);
        sb.append("</").append(name).append('>');
    }

    /** A list under a key repeats the element, so reading it back gives the same list (when it has 2+ items). */
    private static List<Child> fields(Map<?, ?> map) {
        val children = new ArrayList<Child>();
        for (val entry : map.entrySet()) {
            add(children, String.valueOf(entry.getKey()), entry.getValue());
        }
        return children;
    }

    private static List<Child> components(Object value, Class<?> klass) {
        val children = new ArrayList<Child>();
        for (val component : klass.getRecordComponents()) {
            try {
                // setAccessible: non-public record (nested, package-private) also serializes.
                val accessor = component.getAccessor();
                accessor.setAccessible(true);
                add(children, component.getName(), accessor.invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(Errors.get().serializeFailed(klass).value(), e);
            }
        }
        return children;
    }

    private static void add(List<Child> children, String name, @Nullable Object value) {
        switch (value) {
            case Iterable<?> it -> it.forEach(item -> children.add(new Child(name, item)));
            case Object[] arr -> Arrays.stream(arr).forEach(item -> children.add(new Child(name, item)));
            case null, default -> children.add(new Child(name, value));
        }
    }

    private static List<Child> items(Iterable<?> it) {
        val children = new ArrayList<Child>();
        it.forEach(item -> children.add(new Child(ITEM, item)));
        return children;
    }

    private static String text(Object value) {
        return switch (value) {
            case UUID id -> id.toString();
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
    }

    private static void newLine(StringBuilder sb, String indent, int depth) {
        if (indent.isEmpty()) return;
        sb.append('\n').repeat(indent, depth);
    }

    /** XML 1.0 name without namespace prefix: letter or {@code _}, then letters, digits, {@code - _ .}. */
    private static void checkName(String name) {
        var valid = !name.isEmpty() && (Character.isLetter(name.charAt(0)) || name.charAt(0) == '_');
        for (int i = 1; valid && i < name.length(); i++) {
            val c = name.charAt(i);
            valid = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
        }
        if (!valid) throw new IllegalArgumentException(Errors.get().xmlInvalidName(name).value());
    }

    private static void appendText(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            val c = s.charAt(i);
            val escape = ESCAPES.get(c);
            if (escape != null) {
                sb.append(escape);
            } else {
                checkChar(c);
                sb.append(c);
            }
        }
    }

    /** XML 1.0 has no representation for these, not even as a character reference. */
    private static void checkChar(char c) {
        if (c < 0x20 && c != '\t' && c != '\n' || c == 0xFFFE || c == 0xFFFF) {
            throw new IllegalArgumentException(Errors.get().xmlInvalidChar("U+%04X".formatted((int) c)).value());
        }
    }
}
