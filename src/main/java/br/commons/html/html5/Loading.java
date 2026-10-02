package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * When to load an image or iframe ({@code loading}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-loading">HTML: the loading attribute</a>
 */
@NullMarked
public enum Loading implements Keyword {

    /** Loads at once; the default. */
    EAGER("eager"),

    /** Defers loading until the element is about to enter the viewport. */
    LAZY("lazy");

    private final String token;

    Loading(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
