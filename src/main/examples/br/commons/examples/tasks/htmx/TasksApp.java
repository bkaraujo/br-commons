package br.commons.examples.tasks.htmx;

import br.Application;
import br.commons.Logger;
import br.commons.module.Module;
import br.commons.examples.tasks.shared.TaskStore;
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
 * Example: the task list of {@code tasks.jquery}, same tabs, search and counter, rendered on the server and swapped
 * by htmx. The tasks, their store and the title field are the ones of {@code tasks.shared}.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.commons.examples.tasks.htmx.TasksApp} and
 * open {@code http://localhost:5003} (another port: {@code -Dexec.args=PORT}).
 */
@NullMarked
public final class TasksApp extends Application {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/examples/static");

    private final int port;

    private TasksApp(int port) {
        this.port = port;
        val store = new TaskStore();
        store.add("Ler a documentação do htmx");
        store.add("Renderizar no servidor");
        store.markDone(1, true);

        val assets = new StaticFiles("/static/", "static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO);
        val server = new WebServer(port, List.of(new RequestLoggingFilter(), assets));
        server.register(new TaskRoutes(store));
        attach(Module.of(server));
    }

    public static void main(String[] args) {
        val app = new TasksApp(args.length > 0 ? Integer.parseInt(args[0]) : 5003);
        app.run();
        Logger.info("Tarefas (htmx) em http://localhost:%d", app.port);
    }
}
