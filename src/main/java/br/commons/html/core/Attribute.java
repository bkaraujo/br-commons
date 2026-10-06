package br.commons.html.core;

import br.commons.locale.i18n.Errors;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** An attribute; a {@code null} value renders a boolean attribute ({@code disabled}, {@code required}). */
@NullMarked
public record Attribute(String name, @Nullable String value) implements Content {

    /**
     * Names are written raw, so they are checked here, once, when the node is built — not on render. Accepted:
     * {@code [A-Za-z_:@][A-Za-z0-9_:.@-]*} ({@code data-x}, {@code @click}, {@code x-on:click},
     * {@code hx-on::after-request}). Space, {@code =}, quotes, {@code <}, {@code >} and {@code /} would inject markup.
     */
    public Attribute {
        if (!Names.isAttribute(name)) throw new IllegalArgumentException(Errors.get().invalidAttributeName(name).value());
    }

    void render(StringBuilder out) {
        out.append(' ').append(name);
        if (value != null) {
            out.append("=\"");
            Escape.into(out, value);
            out.append('"');
        }
    }

}
