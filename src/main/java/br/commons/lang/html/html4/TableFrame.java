package br.commons.lang.html.html4;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Visible sides of a table frame ({@code frame} of {@code table}).
 *
 * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-frame">HTML 4.01: the frame attribute</a>
 */
@NullMarked
public enum TableFrame implements Keyword {

    /** No sides. */
    VOID("void"),

    /** The top side. */
    ABOVE("above"),

    /** The bottom side. */
    BELOW("below"),

    /** The top and bottom sides. */
    HSIDES("hsides"),

    /** The left-hand side. */
    LHS("lhs"),

    /** The right-hand side. */
    RHS("rhs"),

    /** The right and left sides. */
    VSIDES("vsides"),

    /** All four sides. */
    BOX("box"),

    /** All four sides. */
    BORDER("border");

    private final String token;

    TableFrame(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
