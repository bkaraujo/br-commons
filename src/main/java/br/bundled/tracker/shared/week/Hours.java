package br.bundled.tracker.shared.week;

import br.commons.locale.l10n.L10N;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How hours and days are written on both screens: {@code 7h30}, {@code 7.50} (for a meter), and dates in the locale of
 * the process ({@link L10N#locale()}), from CLDR skeletons: {@code ter., 06/10} in pt-BR, {@code Tue, 10/6} in en-US.
 */
@NullMarked
public final class Hours {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofLocalizedPattern("MEd").withLocale(L10N.locale());
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofLocalizedPattern("Md").withLocale(L10N.locale());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedPattern("yMd").withLocale(L10N.locale());

    private Hours() {}

    /** {@code 7h30}. */
    public static String of(Duration duration) {
        return "%dh%02d".formatted(duration.toHours(), duration.toMinutesPart());
    }

    /** Hours as a decimal number, for a {@code meter}. */
    public static String decimal(Duration duration) {
        return String.format(Locale.ROOT, "%.2f", duration.toMinutes() / 60.0);
    }

    /** {@code seg., 28/09}. */
    public static String day(LocalDate date) {
        return DAY.format(date);
    }

    /** {@code 28/09 – 04/10/2026}. */
    public static String range(LocalDate first, LocalDate last) {
        return DAY_MONTH.format(first) + " – " + DATE.format(last);
    }
}
