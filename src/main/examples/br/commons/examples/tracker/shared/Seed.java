package br.commons.examples.tracker.shared;

import br.commons.examples.tracker.shared.entry.EntryInput;
import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.person.Person;
import br.commons.examples.tracker.shared.person.PersonInput;
import br.commons.examples.tracker.shared.project.Project;
import br.commons.examples.tracker.shared.project.ProjectInput;
import br.commons.examples.tracker.shared.registry.Registry;
import br.commons.examples.tracker.shared.week.Week;
import br.commons.examples.tracker.shared.week.WeekState;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

/** Projects, people and some entries in the current week, to start with totals. */
@NullMarked
public final class Seed {

    private Seed() {}

    /** Empty stores on the system clock, seeded. */
    public static WeekState state() {
        val state = new WeekState(new EntryStore(), new Registry<>(), new Registry<>(), Clock.systemDefaultZone());
        into(state);
        return state;
    }

    public static void into(WeekState state) {
        val projects = state.projects();
        val people = state.people();
        val engine = projects.add(id -> Project.of(id, new ProjectInput("br-engine", "Interno")));
        val portal = projects.add(id -> Project.of(id, new ProjectInput("Portal", "Cliente ACME")));
        projects.add(id -> Project.of(id, new ProjectInput("Legado", "Cliente antigo")).toggled());
        val ana = people.add(id -> Person.of(id, new PersonInput("Ana Souza", "ana@exemplo.com")));
        val bruno = people.add(id -> Person.of(id, new PersonInput("Bruno Lima", "bruno@exemplo.com")));
        people.add(id -> Person.of(id, new PersonInput("Carla Dias", "carla@exemplo.com")).toggled());

        val monday = Week.of(state.today()).monday();
        val entries = state.entries();
        entries.add(entry(monday, engine, ana, "DSL HTML 4/5", "09:00", "12:00"));
        entries.add(entry(monday, engine, ana, "Suporte a htmx", "13:00", "17:30"));
        entries.add(entry(monday, portal, bruno, "Revisão de requisitos", "09:30", "11:00"));
        entries.add(entry(monday.plusDays(1), engine, bruno, "Exemplos", "11:00", "12:30"));
        entries.add(entry(monday.plusDays(1), portal, ana, "Reunião", "14:00", "15:00"));
    }

    private static EntryInput entry(LocalDate date, Project project, Person person, String description, String start, String end) {
        return new EntryInput(date, project.id(), person.id(), description, LocalTime.parse(start), LocalTime.parse(end));
    }
}
