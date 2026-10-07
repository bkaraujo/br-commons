package br.commons.examples.tracker.shared.week;

import br.commons.lang.html.form.InputType;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Stream;

/** An ISO week, Monday to Sunday, written as the HTML {@code week} value ({@code 2026-W40}). */
@NullMarked
public record Week(LocalDate monday) {


    public static Week of(LocalDate date) {
        return new Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** The week of the token; the current week when it is absent or invalid. */
    public static Week parse(@Nullable String token, LocalDate today) {
        if (token == null || !InputType.WEEK.accepts(token)) {
            return of(today);
        }
        val separator = token.indexOf("-W");
        val year = Integer.parseInt(token.substring(0, separator));
        val week = Integer.parseInt(token.substring(separator + 2));
        return of(LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week));
    }

    public String token() {
        return "%d-W%02d".formatted(monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public LocalDate sunday() {
        return monday.plusDays(6);
    }

    public Week previous() {
        return new Week(monday.minusWeeks(1));
    }

    public Week next() {
        return new Week(monday.plusWeeks(1));
    }

    public List<LocalDate> days() {
        return Stream.iterate(monday, day -> day.plusDays(1)).limit(7).toList();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(monday) && !date.isAfter(sunday());
    }

    /** {@code 28/09 – 04/10/2026}, in the locale of the process. */
    public String label() {
        return Hours.range(monday, sunday());
    }
}
