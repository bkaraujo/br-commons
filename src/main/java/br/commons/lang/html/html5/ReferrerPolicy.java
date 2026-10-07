package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Referrer sent with a fetch ({@code referrerpolicy}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-referrerpolicy">HTML: the referrerpolicy attribute</a>
 * @see <a href="https://www.w3.org/TR/referrer-policy/#referrer-policies">Referrer Policy: referrer policies</a>
 */
@NullMarked
public enum ReferrerPolicy implements Keyword {

    /** Sends no {@code Referer}. */
    NO_REFERRER("no-referrer"),

    /** Sends the full URL, except from HTTPS to HTTP. */
    NO_REFERRER_WHEN_DOWNGRADE("no-referrer-when-downgrade"),

    /** Sends only the origin. */
    ORIGIN("origin"),

    /** Full URL to the same origin; only the origin to others. */
    ORIGIN_WHEN_CROSS_ORIGIN("origin-when-cross-origin"),

    /** Full URL to the same origin; nothing to others. */
    SAME_ORIGIN("same-origin"),

    /** Only the origin, and nothing from HTTPS to HTTP. */
    STRICT_ORIGIN("strict-origin"),

    /** Full URL to the same origin, the origin to others, nothing from HTTPS to HTTP; the default. */
    STRICT_ORIGIN_WHEN_CROSS_ORIGIN("strict-origin-when-cross-origin"),

    /** Always the full URL, even from HTTPS to HTTP: leaks paths and queries. */
    UNSAFE_URL("unsafe-url");

    private final String token;

    ReferrerPolicy(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
