package br.commons.html;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Text direction of the {@code dir} global attribute common to HTML 4 and 5; HTML 5 adds {@code auto}.
 *
 * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-dir">HTML: the dir attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/struct/dirlang.html#adef-dir">HTML 4.01: the dir attribute</a>
 */
@NullMarked
public enum Direction implements Keyword {

    /** Left-to-right text. */
    LTR("ltr"),

    /** Right-to-left text. */
    RTL("rtl");

    private final String token;

    Direction(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
