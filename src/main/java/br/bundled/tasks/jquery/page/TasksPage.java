package br.bundled.tasks.jquery.page;

import br.bundled.tasks.shared.TaskFields;
import br.commons.Result;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.html5.CrossOrigin;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.server.WebResource;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static br.commons.lang.Html5.*;

/**
 * {@code GET /}: the skeleton of the page, written with the library's HTML 5 DSL and holding no data. jQuery
 * ({@code static/tasks/jquery/app.js}) fills it from the JSON API and renders every change in the browser; the
 * {@code template} is the row it clones for each task. The tab in the URL ({@code /?filter=open}) is also read
 * by the script.
 */
@NullMarked
public final class TasksPage implements WebResource {

    /** jQuery 4 from its CDN, with the SRI hash of the published file. */
    private static final String JQUERY = "https://code.jquery.com/jquery-4.0.0.min.js";
    private static final String JQUERY_INTEGRITY = "sha384-fgGyf7Mo7DURSOMnOy7ed+dkq5Job205Gnzu6QIg0BOHKaqt4D76Dt8VlDCzcMHV";

    @Override
    public List<Result<Route, String>> routes() {
        return List.of(
                Route.get("/", MediaType.TEXT_HTML, String.class, true, _ -> WebResponse.html(HTTPStatus.OK, skeleton())));
    }

    static String skeleton() {
        return document(html(
                lang("pt-BR"),

                head(metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        title(text(message("Tarefas"))),
                        link(rel("stylesheet"), href("/static/tasks/app.css")),
                        script(src(JQUERY), integrity(JQUERY_INTEGRITY), crossorigin(CrossOrigin.ANONYMOUS)),
                        script(src("/static/tasks/jquery/app.js"), defer())
                ),
                body(
                        header(h1(text(message("Tarefas"))), p(id("counter"), cls("badge"), role("status"))),
                        main(
                                form(id("new-task"), cls("new-task"),
                                        label(htmlFor("title"), text(message("Nova tarefa"))),
                                        input(DIALECT, TaskFields.TITLE, "", id("title"), autofocus(), placeholder(message("o que fazer?")), autocomplete("off")),
                                        button(type(ButtonType.SUBMIT), text(message("Adicionar"))),
                                        p(id("title-error"), cls("error"), role("alert"), hidden())),
                                nav(cls("tabs"), ariaLabel(message("Filtro")), ul(
                                        tab("all", "Todas"), tab("open", "Abertas"), tab("done", "Concluídas"))),
                                search(
                                        label(htmlFor("q"), text(message("Buscar"))),
                                        input(type("search"), id("q"), placeholder(message("título contém…")))),
                                p(id("status"), cls("error"), role("alert"), hidden()),
                                ul(id("task-list"), cls("tasks")),
                                template(id("task-template"), li(cls("task"),
                                        button(type(ButtonType.BUTTON), cls("toggle")),
                                        span(cls("title")),
                                        button(type(ButtonType.BUTTON), cls("delete"), text(message("Excluir")))))),
                        footer(small(text(message("jQuery no navegador, API JSON em br.commons.web"))))
                )
        ));
    }

    /** A tab: a real link ({@code /?filter=open}) that the script turns into a change of view without reload. */
    private static Node tab(String filter, String label) {
        return li(a(href("/?filter=" + filter), data("filter", filter), text(message(label))));
    }

    private static Text message(String text) {
        return Text.message(text);
    }
}
