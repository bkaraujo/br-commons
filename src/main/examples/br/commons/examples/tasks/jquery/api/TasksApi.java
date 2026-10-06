package br.commons.examples.tasks.jquery.api;

import br.commons.Result;
import br.commons.examples.tasks.shared.TaskFields;
import br.commons.examples.tasks.shared.TaskStore;
import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPResponse;
import br.commons.web.ProblemDetail;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The JSON API the page's jQuery calls. Nothing here knows HTML:
 * <ul>
 *   <li>{@code GET /api/tasks} — every task: {@code [{"id": 1, "title": "…", "done": false}]};</li>
 *   <li>{@code POST /api/tasks} {@code {"title": "…"}} — 201 with the task, or 422 with the errors per field;</li>
 *   <li>{@code PATCH /api/tasks/{id}} {@code {"done": true}} — the changed task, or 404;</li>
 *   <li>{@code DELETE /api/tasks/{id}} — 204, or 404.</li>
 * </ul>
 * A body that is not the expected JSON gets a 400 {@code ProblemDetail}.
 */
@NullMarked
public final class TasksApi implements WebResource {

    private final TaskStore store;

    public TasksApi(TaskStore store) {
        this.store = store;
    }

    @Override
    public void routes(Router router) {
        router.get("/api/tasks", MediaType.APPLICATION_JSON, List.class, request -> WebResponse.ok(store.all()))
                .getOrThrow();
        router.post("/api/tasks", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, NewTask.class, Object.class,
                this::create).getOrThrow();
        router.patch("/api/tasks/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, TaskChange.class,
                Object.class, this::change).getOrThrow();
        router.delete("/api/tasks/{id}", MediaType.APPLICATION_JSON, Void.class, this::delete).getOrThrow();
    }

    private HTTPResponse create(WebRequest request) {
        return switch (request.body(NewTask.class)) {
            case Result.Success<NewTask, String>(var body) -> {
                val title = body == null || body.title() == null ? "" : body.title().strip();
                val violations = TaskFields.check(title);
                yield violations.isEmpty()
                        ? WebResponse.created(store.add(title))
                        : WebResponse.json(HTTPStatus.UNPROCESSABLE_CONTENT, new Rejection(violations.stream()
                                .map(violation -> new FieldError(violation.field(), TaskFields.message(violation)))
                                .toList()));
            }
            case Result.Failure<NewTask, String>(var error) -> badRequest(error);
        };
    }

    private HTTPResponse change(WebRequest request) {
        return switch (request.body(TaskChange.class)) {
            case Result.Success<TaskChange, String>(var body) -> body == null
                    ? badRequest("Corpo vazio")
                    : store.markDone(id(request), body.done())
                            .map(WebResponse::ok)
                            .orElseGet(TasksApi::notFound);
            case Result.Failure<TaskChange, String>(var error) -> badRequest(error);
        };
    }

    private HTTPResponse delete(WebRequest request) {
        return store.remove(id(request)) ? WebResponse.noContent() : notFound();
    }

    /** The {@code {id}} of the route; {@code -1} (no task) when it is not a number, which answers 404. */
    private static int id(WebRequest request) {
        val raw = request.pathParam("id").getOrElse("");
        return raw.matches("\\d{1,9}") ? Integer.parseInt(raw) : -1;
    }

    private static HTTPResponse notFound() {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND, "Tarefa não encontrada"));
    }

    private static HTTPResponse badRequest(String error) {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.BAD_REQUEST, error));
    }
}
