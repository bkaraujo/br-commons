package br.bundled.judas._2_infrastructure.web.views;

import br.commons.lang.Html5;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.lang.htmx.ScrollEdge;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import br.commons.locale.l10n.L10N;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

import static br.bundled.judas._2_infrastructure.web.views.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The page around every screen: sticky header with brand, menu and theme switch, {@code <main id="content">} and the
 * status footer (UI-10, UI-11). A boosted navigation gets only the title and the content (UI-20, UI-21).
 */
@NullMarked
public final class Layout {

    /** The id of {@code <main>}, where each screen goes. */
    public static final String CONTENT = "content";

    private static final String STATIC = "/static/judas/";

    /** The new content replaces the old one and the window goes back to the top. */
    private static final SwapSpec NAVIGATION = SwapSpec.of(HxSwap.OUTER_HTML).show("window", ScrollEdge.TOP);

    private Layout() {}

    /** The whole page of {@code screen}, or only its title and content for a boosted navigation. */
    public static HTTPResponse page(HTTPRequest request, Screen screen, Node content) {
        if (!Ui.boosted(request)) return Ui.html(HTTPStatus.OK, document(screen, content));
        val changed = Html.fragment(pageTitle(screen), content(screen, content));
        return HtmxResponse.of(Ui.html(HTTPStatus.OK, changed.render())).retarget("#" + CONTENT).reswap(NAVIGATION).response();
    }

    private static String document(Screen screen, Node content) {
        return Html5.document(html(
                lang(L10N.locale().toLanguageTag()),
                head(
                        metaCharset("utf-8"),
                        script(src(STATIC + "theme.js")),
                        pageTitle(screen),
                        link(rel("stylesheet"), href(STATIC + "judas.css")),
                        htmxScript(),
                        script(src(STATIC + "header.js"), defer())
                ),
                body(
                        hxBoost(true),
                        header(cls("top"), brand(), menu(screen), themeSwitch()),
                        content(screen, content),
                        footer(small(text(message("forms.layout.status"))))
                )
        ));
    }

    /** "Tela · Produto" (UI-11). */
    private static Node pageTitle(Screen screen) {
        return title(text(message("forms.layout.title", screen.label())));
    }

    /** The content; {@code data-screen} tells {@code header.js} which link of the menu to mark (UI-12). */
    private static Node content(Screen screen, Node content) {
        return Html5.main(id(CONTENT), data("screen", screen.name()), content);
    }

    private static Node brand() {
        return a(cls("brand"), href(Screen.HOME.path()),
                span(cls("logo"), ariaHidden(true), text(message("forms.layout.logo"))),
                strong(text(message("forms.layout.product"))));
    }

    /** The menu, sent only with the whole page, the link of {@code current} marked (UI-12). */
    private static Node menu(Screen current) {
        return nav(cls("menu"), ariaLabel(message("forms.layout.menu")),
                ul(each(List.of(Screen.values()), screen -> li(menuLink(screen, screen == current)))));
    }

    private static Node menuLink(Screen screen, boolean current) {
        val attributes = new ArrayList<Content>();
        attributes.add(href(screen.path()));
        attributes.add(data("screen", screen.name()));
        if (current) attributes.add(ariaCurrent("page"));
        attributes.add(span(cls("icon"), ariaHidden(true), screen.icon().svg()));
        attributes.add(span(cls("label"), data("label", screen.label()), text(Text.message(screen.label()))));
        return a(attributes.toArray(Content[]::new));
    }

    /** Born hidden, holding its place, and revealed by {@code theme.js}: no dead buttons without JavaScript (UI-80). */
    private static Node themeSwitch() {
        return div(cls("theme"), hidden(), role("group"), ariaLabel(message("forms.layout.theme")),
                each(List.of(Theme.values()), theme -> button(type(ButtonType.BUTTON), data("theme-choice", theme.token()),
                        title(Text.message(theme.label())), ariaLabel(Text.message(theme.label())), theme.icon().svg())));
    }
}
