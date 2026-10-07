package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Browsing context keyword of {@code target} ({@code a}, {@code area}, {@code base}, {@code form}); a name is also accepted.
 *
 * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-target">HTML: the target attribute</a>
 * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-target">HTML: the target attribute</a>
 */
@NullMarked
public enum LinkTarget implements Keyword {

    /** The current navigable (browsing context); the default. */
    SELF("_self"),

    /** A new navigable (new tab or window). With {@code a}, implies {@code rel="noopener"}. */
    BLANK("_blank"),

    /** The parent navigable, or the current one when there is none. */
    PARENT("_parent"),

    /** The topmost navigable, or the current one when there is none. */
    TOP("_top");

    private final String token;

    LinkTarget(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
