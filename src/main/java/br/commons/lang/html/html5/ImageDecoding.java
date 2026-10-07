package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * How to decode an image ({@code decoding} of {@code img}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-decoding">HTML: the decoding attribute</a>
 */
@NullMarked
public enum ImageDecoding implements Keyword {

    /** Decodes the image together with the other content, presenting both at once. */
    SYNC("sync"),

    /** Decodes the image apart, presenting other content first. */
    ASYNC("async"),

    /** No preference; the default. */
    AUTO("auto");

    private final String token;

    ImageDecoding(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
