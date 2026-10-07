package br.commons.lang.htmx;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Edge of {@code scroll} and {@code show} swap modifiers.
 *
 * @see <a href="https://htmx.org/attributes/hx-swap/">htmx: hx-swap, scroll and show modifiers</a>
 */
@NullMarked
public enum ScrollEdge implements Keyword {

    TOP("top"),
    BOTTOM("bottom");

    private final String token;

    ScrollEdge(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
