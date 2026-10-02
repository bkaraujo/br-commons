package br.commons.html.html4;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * How to read the {@code value} of a {@code param} ({@code valuetype}).
 *
 * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-valuetype">HTML 4.01: the valuetype attribute</a>
 */
@NullMarked
public enum ParamValueType implements Keyword {

    /** The value is passed to the object as a string; the default. */
    DATA("data"),

    /** The value is a URI of a resource with run-time values; {@code type} gives its content type. */
    REF("ref"),

    /** The value is the id of an {@code object} in the same document. */
    OBJECT("object");

    private final String token;

    ParamValueType(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
