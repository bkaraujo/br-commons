package br.commons.examples.tasks.htmx;

import br.commons.examples.tasks.shared.TaskFields;
import br.commons.examples.tasks.shared.TaskStore;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import br.commons.web.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Routes of the task list, all answering HTML:
 * <ul>
 *   <li>{@code GET /?filter=open&q=…} — the page; only {@code #view} (and the counter) when htmx asks for it;</li>
 *   <li>{@code POST /tasks} — adds a task: {@code #view}, the counter and an empty form; invalid, the form with
 *       its error; without htmx, post/redirect/get;</li>
 *   <li>{@code POST /tasks/{id}/done} — marks it done or open: {@code #view} and the counter;</li>
 *   <li>{@code DELETE /tasks/{id}} — deletes it: {@code #view} and the counter.</li>
 * </ul>
 * The actions send the tab and the search, so the answer is the list the person was looking at.
 */
@NullMarked
final class TaskRoutes implements WebResource {

    private final TaskStore store;

    TaskRoutes(TaskStore store) {
        this.store = store;
    }

    @Override
    public void routes(Router router) {
        router.get("/", MediaType.TEXT_HTML, String.class, this::home).getOrThrow();
        router.post("/tasks", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create)
                .getOrThrow();
        router.post("/tasks/{id}/done", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class,
                this::toggle).getOrThrow();
        router.delete("/tasks/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse home(WebRequest request) {
        val view = TaskView.of(request::parameter);
        val htmx = HtmxRequest.of(request);
        val partial = htmx.isHtmx() && !htmx.boosted() && !htmx.historyRestore();
        val response = partial ? view(view) : ok(TaskViews.page(store.all(), view, "", List.of()));
        return response.header("Vary", "HX-Request");
    }

    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val view = TaskView.of(form::value);
        val title = form.value(TaskFields.TITLE.name()).strip();
        val errors = TaskFields.check(title);
        val htmx = HtmxRequest.of(request).isHtmx();
        if (errors.isEmpty()) {
            store.add(title);
            return htmx ? withEmptyForm(view) : WebResponse.redirect(view.url());
        }
        return htmx
                ? HtmxResponse.of(ok(TaskViews.newTask(title, errors, false).render()))
                        .retarget("#new-task").reswap(HxSwap.OUTER_HTML).response()
                : ok(TaskViews.page(store.all(), view, title, errors));
    }

    private HTTPResponse toggle(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        store.toggle(id(request));
        return view(TaskView.of(form::value));
    }

    /** htmx sends the included fields of a {@code DELETE} in the query string. */
    private HTTPResponse delete(WebRequest request) {
        store.remove(id(request));
        return view(TaskView.of(request::parameter));
    }

    /** {@code #view} and the counter out of band. */
    private HTTPResponse view(TaskView view) {
        val tasks = store.all();
        return ok(TaskViews.viewOf(tasks, view).render() + TaskViews.counter(tasks, true).render());
    }

    /** After adding: the list, the counter and an empty form, both out of band. */
    private HTTPResponse withEmptyForm(TaskView view) {
        val tasks = store.all();
        return ok(TaskViews.viewOf(tasks, view).render() + TaskViews.counter(tasks, true).render()
                + TaskViews.newTask("", List.of(), true).render());
    }

    /** The {@code {id}} of the route; {@code -1} (no task) when it is not a number. */
    private static int id(WebRequest request) {
        val raw = request.pathParam("id").getOrElse("");
        return raw.matches("\\d{1,9}") ? Integer.parseInt(raw) : -1;
    }

    private static HTTPResponse ok(String html) {
        return WebResponse.html(HTTPStatus.OK, html);
    }
}
