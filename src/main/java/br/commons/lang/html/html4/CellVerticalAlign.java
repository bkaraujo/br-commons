package br.commons.lang.html.html4;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Vertical alignment of cell content ({@code valign} of table parts and cells).
 *
 * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-valign">HTML 4.01: the valign attribute</a>
 */
@NullMarked
public enum CellVerticalAlign implements Keyword {

    /** Flush with the top of the cell. */
    TOP("top"),

    /** Centered vertically. */
    MIDDLE("middle"),

    /** Flush with the bottom of the cell. */
    BOTTOM("bottom"),

    /** The first line of the cells of the row shares a baseline. */
    BASELINE("baseline");

    private final String token;

    CellVerticalAlign(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
