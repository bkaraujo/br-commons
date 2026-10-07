package br.commons.lang.html.html5;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Use of a text track ({@code kind} of {@code track}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-track-kind">HTML: the kind attribute</a>
 */
@NullMarked
public enum TrackKind implements Keyword {

    /**
     * Transcription or translation of the dialogue, for when the sound is available but not understood; the default.
     */
    SUBTITLES("subtitles"),

    /** Transcription of the dialogue, sound effects and other audio, for when the sound is not available. */
    CAPTIONS("captions"),

    /** Text descriptions of the video, to be synthesized as audio. */
    DESCRIPTIONS("descriptions"),

    /** Chapter titles, to navigate the media. */
    CHAPTERS("chapters"),

    /** Data for scripts; not displayed. */
    METADATA("metadata");

    private final String token;

    TrackKind(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
