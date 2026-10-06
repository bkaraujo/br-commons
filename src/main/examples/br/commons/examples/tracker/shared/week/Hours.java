package br.commons.examples.tracker.shared.week;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How hours and days are written on both screens: {@code 7h30}, {@code 7.50} (for a meter), {@code seg 28/09}. */
@NullMarked
public final class Hours {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR"));

    private Hours() {}

    /** {@code 7h30}. */
    public static String of(Duration duration) {
        return "%dh%02d".formatted(duration.toHours(), duration.toMinutesPart());
    }

    /** Hours as a decimal number, for a {@code meter}. */
    public static String decimal(Duration duration) {
        return String.format(Locale.ROOT, "%.2f", duration.toMinutes() / 60.0);
    }

    /** {@code seg 28/09}. */
    public static String day(LocalDate date) {
        return DAY.format(date);
    }
}
