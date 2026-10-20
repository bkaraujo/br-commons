package br.bundled.money.web.ui;

import br.commons.lang.html.ButtonType;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.hxBoost;
import static br.commons.lang.Htmx.htmxScript;

/**
 * The page around every screen: head, the menu and the theme switch. The menu is boosted ({@code hx-boost}): its
 * links load the next page by htmx, swap the body and push the URL, without a full reload. On narrow screens the
 * menu becomes a bar at the bottom, in reach of the thumb. Every {@code <select>} gets a live filter in the browser
 * ({@code static/money/combo.js}); the home's chart is drawn from its table ({@code static/money/chart.js}). The
 * body is boosted too, so every link and form goes by htmx; while developing it is swapped again whenever the code
 * changes ({@code static/money/dev.js}).
 */
@NullMarked
public final class Layout {

    private Layout() {}

    public static String page(Screen current, Node content) {
        return document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"), meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        meta(name("theme-color"), content("#2f7d5b")),
                        script(src("/static/money/theme.js")),
                        title(text(message(current.label() + " · br.money"))),
                        link(rel("stylesheet"), href("/static/money/app.css")),
                        htmxScript(),
                        script(src("/static/money/combo.js"), defer()),
                        script(src("/static/money/chart.js"), defer()),
                        script(src("/static/money/dev.js"), defer())
                ),
                body(
                        hxBoost(true),
                        header(
                                cls("top"),
                                p(cls("brand"), span(cls("logo"), text(Text.data("R$"))), text(Text.data("br.money"))),
                                navigation(current),
                                themeSwitch()
                        ),
                        main(id("content"), content),
                        footer(small(text(message("Mockup · dados fictícios em memória"))))
                )
        ));
    }

    private static Node navigation(Screen current) {
        return nav(
                cls("menu"), hxBoost(true), ariaLabel(message("Menu")), ul(each(List.of(Screen.values()), screen -> li(
                a(href(screen.path()), cls(screen == current ? "current" : ""),
                        span(cls("icon"), ariaHidden(true), text(Text.data(screen.icon()))),
                        span(cls("label"), text(message(screen.label())))))))
        );
    }

    /**
     * The theme switch, all in the browser: {@code theme.js} applies the choice and marks the pressed button. It is
     * born {@code hidden} and the script reveals it, so without JavaScript there are no dead buttons.
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
