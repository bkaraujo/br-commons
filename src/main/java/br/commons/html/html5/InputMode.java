package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Virtual keyboard hint ({@code inputmode}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-inputmode">HTML: the inputmode attribute</a>
 */
@NullMarked
public enum InputMode implements Keyword {

    /** No virtual keyboard: the page provides its own input. */
    NONE("none"),

    /** The keyboard of the locale; the default. */
    TEXT("text"),

    /** Digits and the decimal separator of the locale. */
    DECIMAL("decimal"),

    /** Digits only (PINs, codes). */
    NUMERIC("numeric"),

    /** Digits, {@code *}, {@code #}: a phone keypad. */
    TEL("tel"),

    /** Text, with an enter key labelled for search. */
    SEARCH("search"),

    /** Text, with {@code @} and {@code .} at hand. */
    EMAIL("email"),

    /** Text, with {@code /} and {@code .} at hand. */
    URL("url");

    private final String token;

    InputMode(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
