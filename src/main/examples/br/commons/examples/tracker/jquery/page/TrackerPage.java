package br.commons.examples.tracker.jquery.page;

import br.commons.html.ButtonType;
import br.commons.html.core.Node;
import br.commons.html.html5.CrossOrigin;
import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import br.commons.locale.i18n.Text;
import br.commons.web.router.Router;
import br.commons.web.server.WebResource;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

import static br.commons.html.html5.Html5.*;

/**
 * {@code GET /}, {@code /projects} and {@code /people}: the same skeleton, written with the library's HTML 5 DSL and
 * holding no data. jQuery ({@code static/tracker/jquery/app.js}) reads the path, fills {@code #content} from the JSON
 * API and renders every change in the browser; the menu changes screen with {@code history.pushState}. The theme
 * switch is the one of the htmx version ({@code static/tracker/theme.js}), and so is the CSS.
 */
@NullMarked
public final class TrackerPage implements WebResource {

    /** jQuery 4 from its CDN, with the SRI hash of the published file. */
    private static final String JQUERY = "https://code.jquery.com/jquery-4.0.0.min.js";
    private static final String JQUERY_INTEGRITY = "sha384-fgGyf7Mo7DURSOMnOy7ed+dkq5Job205Gnzu6QIg0BOHKaqt4D76Dt8VlDCzcMHV";

    /** Path and label of each screen of the menu. */
    private static final List<List<String>> SCREENS =
            List.of(List.of("/", "Semana"), List.of("/projects", "Projetos"), List.of("/people", "Pessoas"));

    /** Value of {@code data-theme-choice}, label and icon of each theme. */
    private static final List<List<String>> THEMES =
            List.of(List.of("auto", "Automático", "◐"), List.of("light", "Claro", "☀"), List.of("dark", "Escuro", "☾"));

    @Override
    public void routes(Router router) {
        for (val screen : SCREENS) {
            router.get(screen.getFirst(), MediaType.TEXT_HTML, String.class,
                    request -> WebResponse.html(HTTPStatus.OK, skeleton())).getOrThrow();
        }
    }

    static String skeleton() {
        return document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        script(src("/static/tracker/theme.js")),
                        title(text(message("Horas trabalhadas"))),
                        link(rel("stylesheet"), href("/static/tracker/app.css")),
                        script(src(JQUERY), integrity(JQUERY_INTEGRITY), crossorigin(CrossOrigin.ANONYMOUS)),
                        script(src("/static/tracker/jquery/app.js"), defer())
                ),
                body(
                        header(
                                cls("top"),
                                p(cls("brand"), text(message("Horas trabalhadas"))),
                                nav(cls("menu"), ul(each(SCREENS, screen -> li(a(href(screen.get(0)),
                                        data("screen", screen.get(0)), text(message(screen.get(1)))))))),
                                themeSwitch()
                        ),
                        p(id("status"), cls("error"), role("alert"), hidden()),
                        main(id("content")),
                        footer(small(text(message("SPA com jQuery sobre a API JSON de br.commons.web"))))
                )
        ));
    }

    /** Born {@code hidden}: {@code theme.js} reveals it, so without JavaScript there are no dead buttons. */
    private static Node themeSwitch() {
        return div(cls("theme"), hidden(), role("group"), ariaLabel(message("Tema")),
                each(THEMES, theme -> button(type(ButtonType.BUTTON), data("theme-choice", theme.get(0)),
                        title(message("Tema " + theme.get(1).toLowerCase(Locale.ROOT))),
                        ariaLabel(message("Tema " + theme.get(1))), text(Text.data(theme.get(2))))));
    }

    private static Text message(String text) {
        return Text.message(text);
    }
}
