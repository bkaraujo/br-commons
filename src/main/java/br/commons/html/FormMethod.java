package br.commons.html;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * HTTP method of a {@code form} ({@code method}); HTML 5 adds {@code dialog}.
 *
 * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-method">HTML: the method attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-method">HTML 4.01: the method attribute</a>
 */
@NullMarked
public enum FormMethod implements Keyword {

    /**
     * The form data set goes in the query string of the {@code action} URL; the default. For requests that change
     * nothing.
     */
    GET("get"),

    /** The form data set goes in the request body, encoded by {@code enctype}. */
    POST("post");

    private final String token;

    FormMethod(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
