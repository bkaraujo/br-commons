package br.commons.serializer.document.navigation;

import br.commons.Logger;
import br.commons.Strings;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Navigation over the generic tree that every format produces after parsing:
 * {@link Map} of {@link String} to {@link Object}, {@link List} of {@link Object}, and scalars.
 * It is the common structure between YAML and JSON — nothing here knows the syntax of either.
 *
 * <p>Keys are dotted paths ({@code server.ports.0.number}), where each segment indexes a
 * map by key or a list by position.
 */
@NullMarked
public abstract class Trees {

    private static final Pattern DOT = Pattern.compile("\\.");

    private Trees() {}

    /** Splits the dotted path into segments. */
    public static String[] splitKey(String key) {
        return DOT.split(key);
    }

    /** Resolves the dotted path from the root; {@code null} if any segment does not exist. */
    @Nullable
    public static Object find(Map<String, Object> root, String key) {
        val tokens = DOT.split(key, -1);

        Object container = root;
        for (val token : tokens) {
            if (container instanceof Map<?, ?> map) {
                val next = map.get(token);
                if (next == null) return null;
                container = next;
                continue;
            }

            if (container instanceof List<?> list) {
                container = element(list, token);
                if (container == null) return null;
            }
        }

        return container;
    }

    @Nullable
    private static Object element(List<?> list, String token) {
        try {
            val index = Integer.parseInt(token);
            if (index >= list.size()) {
                Logger.warn("Element index (%d) beyond bounds (%d)", index, list.size() - 1);
                return null;
            }
            return list.get(index);
        } catch (NumberFormatException e) {
            Logger.fatal("Expected a number, got %s", token);
            return null;
        }
    }

    /**
     * Flattens the tree into dotted keys for O(1) lookup. A list is stored twice: whole,
     * under its own key, and element by element, under {@code key.<index>}.
     */
    public static Map<String, Object> flatten(Map<String, Object> root) {
        val flat = new LinkedHashMap<String, Object>();
        flatten(Strings.EMPTY, root, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> target) {
        for (val entry : map.entrySet()) {
            val key = prefix.isEmpty() ? entry.getKey() : prefix + Strings.DOT + entry.getKey();
            val value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else if (value instanceof List<?> list) {
                flatten(key, list, target);
            } else {
                target.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String key, List<?> list, Map<String, Object> target) {
        target.put(key, list);
        for (int i = 0; i < list.size(); i++) {
            val item = list.get(i);
            if (item instanceof Map<?, ?> nested) {
                flatten(key + Strings.DOT + i, (Map<String, Object>) nested, target);
            } else {
                target.put(key + Strings.DOT + i, item);
            }
        }
    }
}
