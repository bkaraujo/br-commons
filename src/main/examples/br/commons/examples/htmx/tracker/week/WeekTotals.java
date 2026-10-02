package br.commons.examples.htmx.tracker.week;

import br.commons.examples.htmx.tracker.entry.Entry;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The totals of what the week screen shows: hours per day (all seven), per project, per person and in all. */
@NullMarked
record WeekTotals(Map<LocalDate, Duration> byDay, Map<String, Duration> byProject, Map<String, Duration> byPerson,
                  Duration total) {

    static WeekTotals of(Week week, List<Entry> entries, WeekState state) {
        val byDay = new LinkedHashMap<LocalDate, Duration>();
        week.days().forEach(day -> byDay.put(day, Duration.ZERO));
        val byProject = new TreeMap<String, Duration>();
        val byPerson = new TreeMap<String, Duration>();
        var total = Duration.ZERO;
        for (val entry : entries) {
            byDay.merge(entry.date(), entry.duration(), Duration::plus);
            byProject.merge(state.projects().nameOf(entry.projectId()), entry.duration(), Duration::plus);
            byPerson.merge(state.people().nameOf(entry.personId()), entry.duration(), Duration::plus);
            total = total.plus(entry.duration());
        }
        return new WeekTotals(byDay, byProject, byPerson, total);
    }

    /** Share of the total, from 0 to 100. */
    long percent(Duration part) {
        return total.isZero() ? 0 : Math.round(100.0 * part.toMinutes() / total.toMinutes());
    }
}
