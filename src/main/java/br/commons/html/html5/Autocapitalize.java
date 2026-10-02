package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Automatic capitalization of entered text ({@code autocapitalize}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-autocapitalize">HTML: the autocapitalize attribute</a>
 */
@NullMarked
public enum Autocapitalize implements Keyword {

    /** No automatic capitalization (also written {@code off}). */
    NONE("none"),

    /** The first letter of each sentence (also written {@code on}). */
    SENTENCES("sentences"),

    /** The first letter of each word. */
    WORDS("words"),

    /** Every letter. */
    CHARACTERS("characters");

    private final String token;

    Autocapitalize(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
