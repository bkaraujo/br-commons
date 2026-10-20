package br.bundled.tasks.jquery;

import br.Application;
import br.bundled.tasks.jquery.api.TasksApi;
import br.bundled.tasks.jquery.page.TasksPage;
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
 * Example: a task list rendered entirely in the browser with jQuery, over a JSON API on the library's own server
 * that keeps the tasks in memory.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java} and open {@code http://localhost:5000} (another port:
 * {@code -Dexec.args=PORT}). Packages: {@code task} (the tasks, their store and the title field), {@code api} (the
 * JSON API), {@code page} (the page skeleton, with the HTML 5 DSL); the browser side is {@code static/tasks/jquery/app.js}
 * and {@code app.css}.
 */
@NullMarked
public final class TasksApp extends Application {

    /** Read from disk while developing, so CSS and JavaScript reload without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/java/br/bundled/tasks/static");

    private TasksApp(int port) {
        val store = new TaskStore();
        store.add("Ler a documentação do jQuery");
        store.add("Consumir a API JSON");
        store.markDone(1, true);

        val server = new WebServer(
                port,
                List.of(
                        new RequestLoggingFilter(),
                        new StaticFiles("/static/tasks/", "br/bundled/tasks/static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO)
                )
        );

        server.register(new TasksPage());
        server.register(new TasksApi(store));
        attach(Module.of(server));
    }

    static void main(String[] args) {
        new TasksApp(args.length > 0 ? Integer.parseInt(args[0]) : 5000).run();
    }
}
