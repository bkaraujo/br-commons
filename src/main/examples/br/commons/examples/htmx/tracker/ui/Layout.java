package br.commons.examples.htmx.tracker.ui;

import br.commons.html.ButtonType;
import br.commons.html.core.Node;
import br.commons.html.html5.CrossOrigin;
import br.commons.i18n.Text;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

import static br.commons.html.html5.Html5.*;
import static br.commons.htmx.Htmx.*;
import static br.commons.examples.htmx.tracker.ui.Ui.message;

/**
 * The page around every screen: head, the menu and the theme switch. The menu is boosted ({@code hx-boost}): its
 * links load the next page by htmx, swap the body and push the URL, without a full reload.
 */
@NullMarked
public final class Layout {

    private Layout() {}

    public static String page(Screen current, Node content) {
        return document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        script(src("/static/tracker/theme.js")),
                        title(text(message(current.label() + " · Horas trabalhadas"))),
                        link(rel("stylesheet"), href("/static/tracker/app.css")),
                        script(src(Ui.HTMX), integrity(Ui.HTMX_INTEGRITY), crossorigin(CrossOrigin.ANONYMOUS))
                ),
                body(
                        header(
                                cls("top"),
                                p(cls("brand"), text(message("Horas trabalhadas"))),
                                navigation(current),
                                themeSwitch()
                        ),
                        main(id("content"), content),
                        footer(small(text(message("SPA com htmx sobre br.commons.web"))))
                )
        ));
    }

    private static Node navigation(Screen current) {
        return nav(
                cls("menu"), hxBoost(true), ul(each(List.of(Screen.values()), screen -> li(
                a(href(screen.path()), cls(screen == current ? "current" : ""), text(message(screen.label()))))))
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
                        title(message("Tema " + theme.label().toLowerCase(Locale.ROOT))),
                        ariaLabel(message("Tema " + theme.label())), text(Text.data(theme.icon())))
                )
        );
    }
}
