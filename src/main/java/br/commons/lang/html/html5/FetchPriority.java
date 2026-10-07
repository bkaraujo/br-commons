package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Relative priority of a fetch ({@code fetchpriority}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-fetchpriority">HTML: the fetchpriority attribute</a>
 */
@NullMarked
public enum FetchPriority implements Keyword {

    /** Fetch before other resources of the same kind. */
    HIGH("high"),

    /** Fetch after other resources of the same kind. */
    LOW("low"),

    /** No preference; the default. */
    AUTO("auto");

    private final String token;

    FetchPriority(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
