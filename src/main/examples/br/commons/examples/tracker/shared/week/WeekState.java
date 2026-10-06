package br.commons.examples.tracker.shared.week;

import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.person.Person;
import br.commons.examples.tracker.shared.project.Project;
import br.commons.examples.tracker.shared.registry.Registry;
import org.jspecify.annotations.NullMarked;

import java.time.Clock;
import java.time.LocalDate;

/** What the week screen reads: the entries, the two registers (for names and choices) and the clock (today). */
@NullMarked
public record WeekState(EntryStore entries, Registry<Project> projects, Registry<Person> people, Clock clock) {

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
