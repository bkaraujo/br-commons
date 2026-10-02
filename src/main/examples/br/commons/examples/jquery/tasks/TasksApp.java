package br.commons.examples.jquery.tasks;

import br.commons.Logger;
import br.commons.examples.jquery.tasks.api.TasksApi;
import br.commons.examples.jquery.tasks.page.TasksPage;
import br.commons.examples.jquery.tasks.task.TaskStore;
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
 * JSON API), {@code page} (the page skeleton, with the HTML 5 DSL); the browser side is {@code static/tasks/app.js}
 * and {@code app.css}.
 */
@NullMarked
public final class TasksApp {

    /** Read from disk while developing, so CSS and JavaScript reload without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/examples/static");

    private TasksApp() {}

    public static void main(String[] args) throws InterruptedException {
        val port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        val store = new TaskStore();
        store.add("Ler a documentação do jQuery");
        store.add("Consumir a API JSON");
        store.markDone(1, true);

        val assets = new StaticFiles("/static/", "static", Files.isDirectory(STATIC_DIR) ? STATIC_DIR : null, Duration.ZERO);
        val server = new WebServer(port, List.of(new RequestLoggingFilter(), assets));
        server.register(new TasksPage());
        server.register(new TasksApi(store));
        server.start().getOrThrow();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Logger.info("Tarefas em http://localhost:%d", port);
        Thread.currentThread().join();
    }
}
