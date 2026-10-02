package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * What to preload of a media ({@code preload} of {@code audio}, {@code video}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-preload">HTML: the preload attribute</a>
 */
@NullMarked
public enum Preload implements Keyword {

    /** Hints that the media is not expected to play: load nothing in advance. */
    NONE("none"),

    /** Hints to load only the metadata (dimensions, duration, first frame). */
    METADATA("metadata"),

    /** Hints that the whole resource may be downloaded in advance. */
    AUTO("auto");

    private final String token;

    Preload(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
