package br.bundled.money.web.ui;

import br.commons.Strings;
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
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The page around every screen: head, the menu and the theme switch. The body is boosted ({@code hx-boost}): every
 * link and form loads the next page by htmx and pushes the URL, without a full reload. The header stays fixed on top
 * and a boosted navigation swaps only {@code <main id="content">}: the server answers it with the title, the content
 * (retargeted there) and the menu out of band, so the scripts in the {@code <head>} load once. On narrow screens the
 * menu becomes a bar at the bottom, in reach of the thumb. The header's real height goes to {@code --top-height}
 * ({@code static/money/header.js}); the month navigation of a screen with a month sits beside the menu
 * ({@code static/money/month.js}). Every {@code <select>} gets a live filter in the browser
 * ({@code static/money/combo.js}); the home's chart is drawn from its table ({@code static/money/chart.js}); the form of splits sums its
 * columns while typing ({@code static/money/splits.js}); the rows of an outline open and close at any level
 * ({@code static/money/outline.js}).
 */
@NullMarked
public final class Layout {

    /** A boosted navigation replaces the content and starts at the top of the window. */
    private static final SwapSpec INTO_CONTENT = SwapSpec.of(HxSwap.OUTER_HTML).show("window", ScrollEdge.TOP);

    private Layout() {}

    public static HTTPResponse page(HTTPRequest request, Screen current, Node content) {
        return page(request, HTTPStatus.OK, current, null, content);
    }

    /** A screen with a month: {@code months} ({@link Ledger#monthNav}) goes to the header. */
    public static HTTPResponse page(HTTPRequest request, Screen current, Node months, Node content) {
        return page(request, HTTPStatus.OK, current, months, content);
    }

    public static HTTPResponse page(HTTPRequest request, HTTPStatus status, Screen current, Node content) {
        return page(request, status, current, null, content);
    }

    /**
     * The whole page, or for a boosted navigation ({@link Ui#boosted}) only what changes in it. That one always goes
     * as {@code 200}: htmx drops an error answer unswapped (a missing account's 404 would look like a dead click); the
     * whole page keeps the real status.
     */
    public static HTTPResponse page(HTTPRequest request, HTTPStatus status, Screen current, @Nullable Node months,
                                    Node content) {
        if (!Ui.boosted(request)) return Ui.html(status, document(current, months, content));
        val changed = Html.fragment(pageTitle(current), main(id("content"), content), navigation(current, hxSwapOob()),
                monthSlot(months, hxSwapOob()));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget("#content").reswap(INTO_CONTENT).response();
    }

    private static String document(Screen current, @Nullable Node months, Node content) {
        return Html5.document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"),
                        meta(name("viewport"), content("width=device-width, initial-scale=1")),
                        meta(name("theme-color"), content("#4a5a1c")),
                        script(src("/static/money/theme.js")),
                        pageTitle(current),
                        link(rel("stylesheet"), href("/static/money/app.css")),
                        htmxScript(),
                        script(src("/static/money/combo.js"), defer()),
                        script(src("/static/money/chart.js"), defer()),
                        script(src("/static/money/header.js"), defer()),
                        script(src("/static/money/month.js"), defer()),
                        script(src("/static/money/tree.js"), defer()),
                        script(src("/static/money/outline.js"), defer()),
                        script(src("/static/money/splits.js"), defer())
                ),
                body(
                        hxBoost(true),
                        header(
                                cls("top"),
                                p(cls("brand"), span(cls("logo"), text(Text.data("R$"))), text(Text.data("br.money"))),
                                navigation(current, Html.fragment()),
                                monthSlot(months, Html.fragment()),
                                themeSwitch()
                        ),
                        main(id("content"), content),
                        footer(small(text(message("Dados em memória"))))
                )
        ));
    }

    private static Node pageTitle(Screen current) {
        return title(text(message(current.label() + " · br.money")));
    }

    /** {@code oob}: {@code hx-swap-oob} when it goes beside the content of a boosted navigation, else nothing. */
    private static Node navigation(Screen current, Content oob) {
        return nav(
                id("menu"),
                cls("menu"),
                oob,
                ariaLabel(message("Menu")),
                ul(each(
                        List.of(Screen.values()),
                        screen -> li(
                                a(
                                        href(screen.path()),
                                        cls(screen == current ? "current" : ""),
                                        span(cls("icon"), ariaHidden(true), screen.icon().svg()),
                                        span(cls("label"), data("label", screen.label()), text(message(screen.label())))
                                )
                        )
                ))
        );
    }

    /**
     * Where the month navigation of the screen goes, beside the menu: empty on a screen without a month. Always sent
     * with a boosted navigation, so the previous screen's months do not stay.
     */
    private static Node monthSlot(@Nullable Node months, Content oob) {
        return div(id("month-slot"), cls("month-slot"), oob, months == null ? Html.fragment() : months);
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
                        title(message("Tema " + Strings.lower(theme.label()))),
                        ariaLabel(message("Tema " + theme.label())), theme.icon().svg())
                )
        );
    }
}
