package br.commons.lang.html.html4;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Rules between table cells ({@code rules} of {@code table}).
 *
 * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-rules">HTML 4.01: the rules attribute</a>
 */
@NullMarked
public enum TableRules implements Keyword {

    /** No rules. */
    NONE("none"),

    /** Rules between row groups and column groups. */
    GROUPS("groups"),

    /** Rules between rows. */
    ROWS("rows"),

    /** Rules between columns. */
    COLS("cols"),

    /** Rules between all rows and columns. */
    ALL("all");

    private final String token;

    TableRules(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
