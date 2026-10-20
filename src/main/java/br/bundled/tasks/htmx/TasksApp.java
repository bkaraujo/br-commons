package br.bundled.tasks.htmx;

import br.Application;
import br.bundled.tasks.shared.TaskStore;
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
 * Example: the task list of {@code tasks.jquery}, same tabs, search and counter, rendered on the server and swapped
 * by htmx. The tasks, their store and the title field are the ones of {@code tasks.shared}.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.bundled.tasks.htmx.TasksApp} and
 * open {@code http://localhost:5003} (another port: {@code -Dexec.args=PORT}).
 */
@NullMarked
public final class TasksApp extends Application {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/java/br/bundled/tasks/static");

    private TasksApp(int port) {
        val store = new TaskStore();
        store.add("Ler a documentação do htmx");
        store.add("Renderizar no servidor");
        store.markDone(1, true);
        val server = new WebServer(
                port,
                List.of(
                        new RequestLoggingFilter(),
                        new StaticFiles("/static/tasks/", "br/bundled/tasks/static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO)
                )
        );
        server.register(new TaskRoutes(store));
        attach(Module.of(server));
    }

    static void main(String[] args) {
        new TasksApp(args.length > 0 ? Integer.parseInt(args[0]) : 5003).run();
    }
}
