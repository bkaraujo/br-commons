package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * How a {@code textarea} wraps the submitted value ({@code wrap}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-wrap">HTML: the wrap attribute</a>
 */
@NullMarked
public enum TextWrap implements Keyword {

    /** The browser inserts line breaks where the text wraps, and submits them; needs {@code cols}. */
    HARD("hard"),

    /** Lines wrap on screen only; the value is submitted as typed; the default. */
    SOFT("soft");

    private final String token;

    TextWrap(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
