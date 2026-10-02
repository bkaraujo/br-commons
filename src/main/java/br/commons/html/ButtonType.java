package br.commons.html;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Behavior of a {@code button} ({@code type}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-button-type">HTML: the type attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-type-BUTTON">HTML 4.01: the type attribute</a>
 */
@NullMarked
public enum ButtonType implements Keyword {

    /** Submits the form owner; the default. */
    SUBMIT("submit"),

    /** Resets the controls of the form owner to their initial values. */
    RESET("reset"),

    /** Does nothing by itself; for scripts and htmx. */
    BUTTON("button");

    private final String token;

    ButtonType(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
