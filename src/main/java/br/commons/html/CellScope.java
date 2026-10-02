package br.commons.html;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Cells a header cell applies to ({@code scope} of {@code th}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-th-scope">HTML: the scope attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-scope">HTML 4.01: the scope attribute</a>
 */
@NullMarked
public enum CellScope implements Keyword {

    /** The header cell applies to the rest of its row. */
    ROW("row"),

    /** The header cell applies to the rest of its column. */
    COL("col"),

    /** The header cell applies to the rest of its row group ({@code tbody}, {@code thead}, {@code tfoot}). */
    ROWGROUP("rowgroup"),

    /** The header cell applies to the rest of its column group ({@code colgroup}). */
    COLGROUP("colgroup");

    private final String token;

    CellScope(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
