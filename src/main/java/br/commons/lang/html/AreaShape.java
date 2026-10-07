package br.commons.lang.html;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Shape of an image map region ({@code shape} of {@code area}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-area-shape">HTML: the shape attribute</a>
 * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-shape">HTML 4.01: the shape attribute</a>
 */
@NullMarked
public enum AreaShape implements Keyword {

    /** A rectangle; {@code coords} is {@code x1,y1,x2,y2}. */
    RECT("rect"),

    /** A circle; {@code coords} is {@code x,y,radius}. */
    CIRCLE("circle"),

    /** A polygon; {@code coords} is {@code x1,y1,x2,y2,…}, at least three points. */
    POLY("poly"),

    /** The whole image; no {@code coords}. */
    DEFAULT("default");

    private final String token;

    AreaShape(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
