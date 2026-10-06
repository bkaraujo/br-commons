package br.commons.examples.tracker.shared;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Ids typed by hand or read from a URL: a positive number, or {@code null} for anything else. */
@NullMarked
public final class Ids {

    private Ids() {}

    /** A positive number, or {@code null} for anything else (absent, empty, not a number). */
    public static @Nullable Integer number(@Nullable String value) {
        return value != null && value.matches("\\d{1,9}") ? Integer.valueOf(value) : null;
    }
}
