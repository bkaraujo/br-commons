package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Popover behavior ({@code popover}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/popover.html#attr-popover">HTML: the popover attribute</a>
 */
@NullMarked
public enum PopoverState implements Keyword {

    /** Light dismiss (click outside, Esc) and closes other {@code auto} popovers when shown. */
    AUTO("auto"),

    /** Opens and closes only when told to; several can be open. */
    MANUAL("manual"),

    /** Light dismiss; closes other hints but not {@code auto} popovers (tooltips). */
    HINT("hint");

    private final String token;

    PopoverState(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
