package br.bundled.tracker.htmx.ui;

import br.commons.Strings;
import br.commons.lang.Html5;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.lang.htmx.ScrollEdge;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static br.bundled.tracker.htmx.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.htmxScript;
import static br.commons.lang.Htmx.hxBoost;

/**
 * The page around every screen: head, the menu and the theme switch. The menu is boosted ({@code hx-boost}): its
 * links load the next screen by htmx and push the URL, without a full reload, and the server answers them with only
 * the title and {@code <main id="content">} (retargeted there), so the header stays. The menu never comes back: the
 * content says its screen ({@code data-screen}) and the CSS marks its link.
 */
@NullMarked
public final class Layout {

    /** A boosted navigation replaces the content and starts at the top of the window. */
    private static final SwapSpec INTO_CONTENT = SwapSpec.of(HxSwap.OUTER_HTML).show("window", ScrollEdge.TOP);

    private Layout() {}

    /** The whole page, or for a boosted navigation ({@link Ui#boosted}) only the title and the content. */
    public static HTTPResponse page(HTTPRequest request, Screen current, Node content) {
        if (!Ui.boosted(request)) return Ui.ok(document(current, content));
        val changed = Html.fragment(pageTitle(current), contentOf(current, content));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget("#content").reswap(INTO_CONTENT).response();
    }

    private static String document(Screen current, Node content) {
        return Html5.document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        script(src("/static/tracker/theme.js")),
                        pageTitle(current),
                        link(rel("stylesheet"), href("/static/tracker/app.css")),
                        htmxScript()
                ),
                body(
                        header(
                                cls("top"),
                                p(cls("brand"), text(message("Horas trabalhadas"))),
                                navigation(),
                                themeSwitch()
                        ),
                        contentOf(current, content),
                        footer(small(text(message("SPA com htmx sobre br.commons.web"))))
                )
        ));
    }

    private static Node pageTitle(Screen current) {
        return title(text(message(current.label() + " · Horas trabalhadas")));
    }

    /** The content of the screen: its {@code data-screen} marks the menu's link (CSS). */
    private static Node contentOf(Screen current, Node content) {
        return main(id("content"), data("screen", current.name()), content);
    }

    /** The menu, the same on every screen: it goes once, with the page; each link says its screen. */
    private static Node navigation() {
        return nav(
                cls("menu"), hxBoost(true), ul(each(List.of(Screen.values()), screen -> li(
                a(href(screen.path()), data("screen", screen.name()), text(message(screen.label()))))))
        );
    }

    /**
     * The theme switch, all in the browser: {@code theme.js} applies the choice and marks the pressed button. It is
     * born {@code hidden} and the script reveals it, so without JavaScript there are no dead buttons (the theme
     * follows the system).
     */
    private static Node themeSwitch() {
        return div(
                cls("theme"), hidden(), role("group"), ariaLabel(message("Tema")),
                each(
                        List.of(Theme.values()), theme -> button(type(ButtonType.BUTTON), data("theme-choice", theme.token()),
                        title(message("Tema " + Strings.lower(theme.label()))),
                        ariaLabel(message("Tema " + theme.label())), text(Text.data(theme.icon())))
                )
        );
    }
}
