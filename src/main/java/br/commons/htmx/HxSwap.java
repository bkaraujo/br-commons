package br.commons.htmx;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * How the response is swapped into the target ({@code hx-swap}, {@code HX-Reswap}).
 *
 * @see <a href="https://htmx.org/attributes/hx-swap/">htmx: hx-swap</a>
 */
@NullMarked
public enum HxSwap implements Keyword {

    /** Replaces the inner html of the target element. */
    INNER_HTML("innerHTML"),

    /** Replaces the entire target element with the response. */
    OUTER_HTML("outerHTML"),

    /** Replaces the text content of the target, without parsing the response as HTML. */
    TEXT_CONTENT("textContent"),

    /** Inserts the response before the target element. */
    BEFORE_BEGIN("beforebegin"),

    /** Inserts the response before the first child of the target. */
    AFTER_BEGIN("afterbegin"),

    /** Inserts the response after the last child of the target. */
    BEFORE_END("beforeend"),

    /** Inserts the response after the target element. */
    AFTER_END("afterend"),

    /** Deletes the target element regardless of the response. */
    DELETE("delete"),

    /** Does not append content from the response (out of band items are still processed). */
    NONE("none");

    private final String token;

    HxSwap(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
