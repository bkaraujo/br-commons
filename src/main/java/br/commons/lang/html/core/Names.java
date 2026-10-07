package br.commons.lang.html.core;

import lombok.val;
import org.jspecify.annotations.NullMarked;

/**
 * Validation of tag and attribute names, which the renderer writes without escaping. A character loop instead of a
 * regex: it runs for every node the DSL builds.
 */
@NullMarked
final class Names {

    private Names() {}

    /** {@code [A-Za-z][A-Za-z0-9-]*} */
    static boolean isTag(String name) {
        if (name.isEmpty() || !letter(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            val c = name.charAt(i);
            if (!letter(c) && !digit(c) && c != '-') return false;
        }
        return true;
    }

    /** {@code [A-Za-z_:@][A-Za-z0-9_:.@-]*} */
    static boolean isAttribute(String name) {
        if (name.isEmpty()) return false;
        val first = name.charAt(0);
        if (!letter(first) && "_:@".indexOf(first) < 0) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!attributePart(name.charAt(i))) return false;
        }
        return true;
    }

    private static boolean attributePart(char c) {
        return letter(c) || digit(c) || "_:.@-".indexOf(c) >= 0;
    }

    private static boolean letter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
