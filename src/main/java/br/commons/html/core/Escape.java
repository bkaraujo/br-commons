package br.commons.html.core;

import lombok.val;
import org.jspecify.annotations.NullMarked;

/** Escapes {@code & < > " '} for both text and quoted attribute values. */
@NullMarked
final class Escape {

    private static final String[] ENTITIES = new String[128];

    static {
        ENTITIES['&'] = "&amp;";
        ENTITIES['<'] = "&lt;";
        ENTITIES['>'] = "&gt;";
        ENTITIES['"'] = "&quot;";
        ENTITIES['\''] = "&#39;";
    }

    private Escape() {}

    static void into(StringBuilder out, String text) {
        for (var i = 0; i < text.length(); i++) {
            val c = text.charAt(i);
            val entity = c < ENTITIES.length ? ENTITIES[c] : null;
            if (entity == null) {
                out.append(c);
            } else {
                out.append(entity);
            }
        }
    }
}
