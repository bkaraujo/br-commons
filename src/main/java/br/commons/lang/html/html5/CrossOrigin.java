package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * CORS mode of a fetch ({@code crossorigin}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-crossorigin">HTML: the crossorigin attribute</a>
 */
@NullMarked
public enum CrossOrigin implements Keyword {

    /** A CORS request with credentials only for the same origin. */
    ANONYMOUS("anonymous"),

    /** A CORS request that always sends credentials (cookies, HTTP auth, client certificates). */
    USE_CREDENTIALS("use-credentials");

    private final String token;

    CrossOrigin(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
