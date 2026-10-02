package br.commons.html.html4;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Horizontal alignment of cell content ({@code align} of table parts and cells).
 *
 * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-align-TD">HTML 4.01: the align attribute</a>
 */
@NullMarked
public enum CellAlign implements Keyword {

    /** Flush left. */
    LEFT("left"),

    /** Centered. */
    CENTER("center"),

    /** Flush right. */
    RIGHT("right"),

    /** Double-justified. */
    JUSTIFY("justify"),

    /** Aligned around the character of {@code char}, offset by {@code charoff}. */
    CHAR("char");

    private final String token;

    CellAlign(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
