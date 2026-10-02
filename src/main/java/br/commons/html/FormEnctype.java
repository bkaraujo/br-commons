package br.commons.html;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Encoding of the form data set ({@code enctype}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-enctype">HTML: the enctype attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-enctype">HTML 4.01: the enctype attribute</a>
 */
@NullMarked
public enum FormEnctype implements Keyword {

    /** Name/value pairs percent-encoded and joined by {@code &}; the default. */
    URL_ENCODED("application/x-www-form-urlencoded"),

    /** Each control in its own part; required to upload files ({@code input type=file}). */
    MULTIPART("multipart/form-data"),

    /** Name/value pairs as plain lines; not reliably decodable, for debugging only. */
    TEXT_PLAIN("text/plain");

    private final String token;

    FormEnctype(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
