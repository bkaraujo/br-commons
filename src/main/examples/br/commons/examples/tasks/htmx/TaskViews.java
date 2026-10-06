package br.commons.examples.tasks.htmx;

import br.commons.examples.tasks.shared.Task;
import br.commons.examples.tasks.shared.TaskFields;
import br.commons.html.ButtonType;
import br.commons.html.FormMethod;
import br.commons.html.core.Node;
import br.commons.html.form.Violation;
import br.commons.html.html5.CrossOrigin;
import br.commons.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static br.commons.html.html5.Html5.*;
import static br.commons.htmx.Htmx.*;

/**
 * The page and its fragments, rendered on the server. What changes with the tab, the search or a task is inside
 * {@code #view} (the tabs and the list), which every action swaps; the counter of the header comes along out of
 * band. Same markup and CSS ({@code static/tasks/app.css}) as the jQuery version.
 */
@NullMarked
final class TaskViews {

    /** htmx 2 from the CDN, as https://htmx.org/docs/#installing recommends, with its integrity hash. */
    private static final String HTMX = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js";
    private static final String HTMX_INTEGRITY = "sha384-2OatzQy1H+Zd/IIrjr1TcuDGqLXeHhbooAyJY1KdQMKnr4LZ22k31GBLdYKHmVjg";

    /** Every action sends the tab and the search, to answer with the same list. */
    private static final String STATE = "#view [name=filter], #q";

    private TaskViews() {}

    static String page(List<Task> tasks, TaskView view, String title, List<Violation> errors) {
        return document(html(
                lang("pt-BR"),
                head(metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        title(text(message("Tarefas"))),
                        link(rel("stylesheet"), href("/static/tasks/app.css")),
                        script(src(HTMX), integrity(HTMX_INTEGRITY), crossorigin(CrossOrigin.ANONYMOUS))
                ),
                body(
                        header(h1(text(message("Tarefas"))), counter(tasks, false)),
                        main(
                                newTask(title, errors, false),
                                search(form(method(FormMethod.GET), action("/"),
                                        label(htmlFor("q"), text(message("Buscar"))),
                                        input(type("search"), id("q"), name("q"), value(view.query()),
                                                placeholder(message("título contém…")), hxGet("/"),
                                                hxTrigger("input changed delay:300ms, search"), hxTarget("#view"),
                                                hxSwap(HxSwap.OUTER_HTML), hxInclude("#view [name=filter]")),
                                        input(type("hidden"), name("filter"), value(view.filter())))),
                                viewOf(tasks, view)),
                        footer(small(text(message("htmx no navegador, HTML renderizado em br.commons.web"))))
                )
        ));
    }

    /**
     * The new task form; a submit answers with {@code #view}, an invalid one comes back here with the error. Without
     * htmx it posts and redirects. Out of band, it empties the form after a task was added.
     */
    static Node newTask(String title, List<Violation> errors, boolean outOfBand) {
        return form(id("new-task"), cls("new-task"), method(FormMethod.POST), action("/tasks"),
                outOfBand ? hxSwapOob() : fragment(),
                hxPost("/tasks"), hxTarget("#view"), hxSwap(HxSwap.OUTER_HTML), hxInclude(STATE),
                label(htmlFor("title"), text(message("Nova tarefa"))),
                input(DIALECT, TaskFields.TITLE, title, id("title"), autofocus(),
                        placeholder(message("o que fazer?")), autocomplete("off")),
                button(type(ButtonType.SUBMIT), text(message("Adicionar"))),
                errors.isEmpty()
                        ? fragment()
                        : p(id("title-error"), cls("error"), role("alert"),
                                text(message(TaskFields.message(errors.getFirst())))));
    }

    /** The tabs and the list; the tab is also a hidden field, which the actions send. */
    static Node viewOf(List<Task> tasks, TaskView view) {
        val shown = tasks.stream().filter(view::shows).toList();
        return div(id("view"),
                input(type("hidden"), name("filter"), value(view.filter())),
                nav(cls("tabs"), ariaLabel(message("Filtro")), ul(
                        tab(view, "all", "Todas"), tab(view, "open", "Abertas"), tab(view, "done", "Concluídas"))),
                ul(id("task-list"), cls("tasks"),
                        each(shown, TaskViews::item),
                        when(shown.isEmpty(), () -> li(cls("empty"), text(message("Nenhuma tarefa"))))));
    }

    /** A real link ({@code /?filter=open}) that htmx turns into a swap of {@code #view}, pushed to the history. */
    private static Node tab(TaskView view, String filter, String label) {
        val url = TaskView.url(filter);
        val current = filter.equals(view.filter());
        return li(a(href(url), hxGet(url), hxTarget("#view"), hxSwap(HxSwap.OUTER_HTML), hxPushUrl(true),
                hxInclude("#q"), cls(current ? "current" : ""),
                text(message(label))));
    }

    private static Node item(Task task) {
        val path = "/tasks/" + task.id();
        return li(cls(task.done() ? "task done" : "task"), data("id", String.valueOf(task.id())),
                button(type(ButtonType.BUTTON), cls("toggle"), ariaLabel(message(task.done() ? "Reabrir" : "Concluir")),
                        hxPost(path + "/done"), hxTarget("#view"), hxSwap(HxSwap.OUTER_HTML), hxInclude(STATE),
                        text(Text.data(task.done() ? "✓" : ""))),
                span(cls("title"), text(Text.data(task.title()))),
                button(type(ButtonType.BUTTON), cls("delete"), hxDelete(path), hxTarget("#view"),
                        hxSwap(HxSwap.OUTER_HTML), hxInclude(STATE),
                        hxConfirm(message("Excluir \"" + task.title() + "\"?")), text(message("Excluir"))));
    }

    /** {@code 2 aberta(s) de 5}; out of band when it comes along with {@code #view}. */
    static Node counter(List<Task> tasks, boolean outOfBand) {
        val open = tasks.stream().filter(task -> !task.done()).count();
        return p(id("counter"), cls("badge"), role("status"), outOfBand ? hxSwapOob() : fragment(),
                text(Text.data(open + " aberta(s) de " + tasks.size())));
    }

    private static Text message(String text) {
        return Text.message(text);
    }
}
