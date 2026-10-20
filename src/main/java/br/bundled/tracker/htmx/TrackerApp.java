package br.bundled.tracker.htmx;

import br.Application;
import br.bundled.tracker.htmx.person.PersonRoutes;
import br.bundled.tracker.htmx.project.ProjectRoutes;
import br.bundled.tracker.htmx.week.WeekRoutes;
import br.bundled.tracker.shared.Seed;
import br.commons.module.Module;
import br.commons.web.filter.RequestLoggingFilter;
import br.commons.web.filter.StaticFiles;
import br.commons.web.server.WebServer;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Example: a tracker of worked hours, with registers of projects and people, totals per day, project and person,
 * and a light/dark theme switch, as a single page application with htmx on the library's own server.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.bundled.tracker.htmx.TrackerApp}
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
public final class TrackerApp extends Application {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/java/br/bundled/tracker/static");

    private TrackerApp(int port) {
        val state = Seed.state();
        val server = new WebServer(
                port,
                List.of(
                        new RequestLoggingFilter(),
                        new StaticFiles("/static/tracker/", "br/bundled/tracker/static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO)
                )
        );

        server.register(new WeekRoutes(state));
        server.register(new ProjectRoutes(state.projects(), state.entries()));
        server.register(new PersonRoutes(state.people(), state.entries()));
        attach(Module.of(server));
    }

    static void main(String[] args) {
        new TrackerApp(args.length > 0 ? Integer.parseInt(args[0]) : 5001).run();
    }
}
