package br.commons.html.core;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** An attribute; a {@code null} value renders a boolean attribute ({@code disabled}, {@code required}). */
@NullMarked
public record Attribute(String name, @Nullable String value) implements Content {

    void render(StringBuilder out) {
        out.append(' ').append(name);
        if (value != null) {
            out.append("=\"");
            Escape.into(out, value);
            out.append('"');
        }
    }

}
