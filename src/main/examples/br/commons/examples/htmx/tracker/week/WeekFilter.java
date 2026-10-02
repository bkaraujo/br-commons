package br.commons.examples.htmx.tracker.week;

import br.commons.examples.htmx.tracker.entry.Entry;
import br.commons.examples.htmx.tracker.ui.Ui;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** What the week screen shows: a week and, optionally, one person. Kept in the URL, so it goes to the history. */
@NullMarked
record WeekFilter(Week week, @Nullable Integer person) {

    /** From the query string; the current week and everybody when absent or invalid. */
    static WeekFilter parse(@Nullable String week, @Nullable String person, LocalDate today) {
        return new WeekFilter(Week.parse(week, today), Ui.number(person));
    }

    WeekFilter previous() {
        return new WeekFilter(week.previous(), person);
    }

    WeekFilter next() {
        return new WeekFilter(week.next(), person);
    }

    /** {@code /?week=2026-W40&person=2}. */
    String url() {
        return "/?week=" + week.token() + (person == null ? "" : "&person=" + person);
    }

    /** The person as a form value; empty for everybody. */
    String personToken() {
        return person == null ? "" : person.toString();
    }

    boolean accepts(Entry entry) {
        return week.contains(entry.date()) && (person == null || person == entry.personId());
    }
}
