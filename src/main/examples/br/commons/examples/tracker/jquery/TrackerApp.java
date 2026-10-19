package br.commons.examples.tracker.jquery;

import br.Application;
import br.commons.Logger;
import br.commons.module.Module;
import br.commons.examples.tracker.jquery.api.RegisterApi;
import br.commons.examples.tracker.jquery.api.WeekApi;
import br.commons.examples.tracker.jquery.page.TrackerPage;
import br.commons.examples.tracker.shared.Seed;
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
 * Example: the tracker of worked hours of {@code tracker.htmx}, same screens and rules, rendered entirely in the
 * browser with jQuery over a JSON API on the library's own server. The models, stores and checks are the ones of
 * {@code tracker.shared}.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.commons.examples.tracker.jquery.TrackerApp}
 * and open {@code http://localhost:5002} (another port: {@code -Dexec.args=PORT}).
 */
@NullMarked
public final class TrackerApp extends Application {

    /** Read from disk while developing, so CSS and JavaScript reload without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/examples/static");

    private final int port;

    private TrackerApp(int port) {
        this.port = port;
        val state = Seed.state();
        val server = new WebServer(port, List.of(new RequestLoggingFilter(),
                new StaticFiles("/static/", "static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO)));
        server.register(new TrackerPage());
        server.register(new WeekApi(state));
        server.register(RegisterApi.projects(state.projects(), state.entries()));
        server.register(RegisterApi.people(state.people(), state.entries()));
        attach(Module.of(server));
    }

    static void main(String[] args) {
        val app = new TrackerApp(args.length > 0 ? Integer.parseInt(args[0]) : 5002);
        app.run();
        Logger.info("Horas (jQuery) em http://localhost:%d", app.port);
    }
}
