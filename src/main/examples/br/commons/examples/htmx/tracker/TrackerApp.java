package br.commons.examples.htmx.tracker;

import br.commons.Logger;
import br.commons.examples.htmx.tracker.entry.EntryInput;
import br.commons.examples.htmx.tracker.entry.EntryStore;
import br.commons.examples.htmx.tracker.person.Person;
import br.commons.examples.htmx.tracker.person.PersonInput;
import br.commons.examples.htmx.tracker.person.PersonRoutes;
import br.commons.examples.htmx.tracker.project.Project;
import br.commons.examples.htmx.tracker.project.ProjectInput;
import br.commons.examples.htmx.tracker.project.ProjectRoutes;
import br.commons.examples.htmx.tracker.registry.Registry;
import br.commons.examples.htmx.tracker.week.WeekState;
import br.commons.examples.htmx.tracker.week.Week;
import br.commons.examples.htmx.tracker.week.WeekRoutes;
import br.commons.logger.LogLevel;
import br.commons.web.filter.RequestLoggingFilter;
import br.commons.web.filter.StaticFiles;
import br.commons.web.server.WebServer;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Example: a tracker of worked hours, with registers of projects and people, totals per day, project and person,
 * and a light/dark theme switch, as a single page application with htmx on the library's own server.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.commons.examples.htmx.tracker.TrackerApp}
 * and open {@code http://localhost:5001} (another port: {@code -Dexec.args=PORT}). It shows:
 * <ul>
 *   <li>a boosted menu ({@code hx-boost}) between the screens, and one URL for page and fragment;</li>
 *   <li>a week filtered by person, both in the URL and in the history ({@code hx-push-url});</li>
 *   <li>fields declared once and checked on the server, with rules across fields and registers (end after start,
 *       active project and person, unique name and e-mail), errors next to each field;</li>
 *   <li>inline edit of table rows ({@code hx-include="closest tr"}), (de)activation, delete refused while in use;</li>
 *   <li>totals updated out of band ({@code hx-swap-oob}) and pushed URLs from the server ({@code HX-Push-Url});</li>
 *   <li>a light/dark theme switch entirely in the browser ({@code static/tracker/theme.js});</li>
 *   <li>post/redirect/get when htmx is not there.</li>
 * </ul>
 * The packages and how they depend on each other are in {@code package-info.java} of this package.
 */
@NullMarked
public final class TrackerApp {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/examples/static");

    private TrackerApp() {}

    static void main(String[] args) throws InterruptedException {
        val entries = new EntryStore();
        val projects = new Registry<Project>();
        val people = new Registry<Person>();
        val clock = Clock.systemDefaultZone();
        seed(entries, projects, people, Week.of(LocalDate.now(clock)));

        val port = args.length > 0 ? Integer.parseInt(args[0]) : 5001;
        val assets = new StaticFiles("/static/", "static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO);
        val server = new WebServer(port, List.of(new RequestLoggingFilter(), assets));
        server.register(new WeekRoutes(new WeekState(entries, projects, people, clock)));
        server.register(new ProjectRoutes(projects, entries));
        server.register(new PersonRoutes(people, entries));
        server.start().getOrThrow();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Logger.info("Horas em http://localhost:%d", port);
        Thread.currentThread().join();
    }

    /** Projects, people and some entries in the current week, to start with totals. */
    private static void seed(EntryStore entries, Registry<Project> projects, Registry<Person> people, Week week) {
        val engine = projects.add(id -> Project.of(id, new ProjectInput("br-engine", "Interno")));
        val judas = projects.add(id -> Project.of(id, new ProjectInput("JUDAS", "Família")));
        projects.add(id -> Project.of(id, new ProjectInput("Legado", "Cliente antigo")).toggled());
        val ana = people.add(id -> Person.of(id, new PersonInput("Ana Souza", "ana@exemplo.com")));
        val bruno = people.add(id -> Person.of(id, new PersonInput("Bruno Lima", "bruno@exemplo.com")));
        people.add(id -> Person.of(id, new PersonInput("Carla Dias", "carla@exemplo.com")).toggled());

        val monday = week.monday();
        entries.add(entry(monday, engine, ana, "DSL HTML 4/5", "09:00", "12:00"));
        entries.add(entry(monday, engine, ana, "Suporte a htmx", "13:00", "17:30"));
        entries.add(entry(monday, judas, bruno, "Revisão de requisitos", "09:30", "11:00"));
        entries.add(entry(monday.plusDays(1), engine, bruno, "Exemplos", "11:00", "12:30"));
        entries.add(entry(monday.plusDays(1), judas, ana, "Reunião", "14:00", "15:00"));
    }

    private static EntryInput entry(LocalDate date, Project project, Person person, String description, String start, String end) {
        return new EntryInput(date, project.id(), person.id(), description, LocalTime.parse(start), LocalTime.parse(end));
    }
}
