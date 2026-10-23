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

import java.util.ArrayList;
import java.util.List;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The page around every screen: head, the menu and the theme switch. The body is boosted ({@code hx-boost}): every
 * link and form loads the next page by htmx and pushes the URL, without a full reload. The header stays fixed on top
 * and a boosted navigation swaps only {@code <main id="content">}: the server answers it with the title, the content
 * (retargeted there) and, out of band, the month navigation when it changed ({@link #MONTH_KEY}), so the scripts in
 * the {@code <head>} load once. The menu never comes back: the content says its screen ({@code data-screen}) and the
 * CSS marks its link. A change made over any screen ({@link #CHANGED}) reloads the content from the URL the browser
 * is on. The header's real height goes to {@code --top-height}
 * ({@code static/money/header.js}); the month navigation of a screen with a month sits beside the menu
 * ({@code static/money/month.js}). Every {@code <select>} gets a live filter in the browser
 * ({@code static/money/combo.js}); the home's chart is drawn from its table ({@code static/money/chart.js}); the form of splits sums its
 * columns while typing ({@code static/money/splits.js}); the rows of an outline open and close at any level
 * ({@code static/money/outline.js}).
 */
@NullMarked
public final class Layout {

    /** The id of {@code <main>}, where every screen goes. */
    public static final String CONTENT = "content";

    /**
     * A change made over any screen (the quick entry): the content reloads itself ({@code hx-get=""}: the URL the
     * browser is on, with its account, month and filters), answered like a boosted navigation ({@link Ui#reload}).
     */
    public static final String CHANGED = "book-changed";

    /**
     * The request header with the key of the month navigation the page shows ({@code month.js} sends the
     * {@code data-key} of {@code #month-slot}): the navigation goes out of band only when its key changed.
     */
    public static final String MONTH_KEY = "Month-Key";

    /** A boosted navigation replaces the content and starts at the top of the window. */
    private static final SwapSpec INTO_CONTENT = SwapSpec.of(HxSwap.OUTER_HTML).show("window", ScrollEdge.TOP);

    /**
     * The content reloaded, or a part of it, keeps the window where it is: a boosted request (the month navigation)
     * would otherwise scroll to the top of its target.
     */
    private static final SwapSpec IN_PLACE = SwapSpec.of(HxSwap.OUTER_HTML).noShow();

    /** Where the quick entry opens over any screen ({@code GET /entries/new}); {@link #ENTRY_EDITOR} empties it. */
    public static final String ENTRY_MODAL = "entry-modal";
    public static final String ENTRY_EDITOR = "/entries/editor";

    /**
     * Closing the quick entry: a click on the backdrop itself, or Escape while its card is open. The filter of htmx
     * must answer {@code true}: ending it with the element itself ({@code querySelector(...)}) never fires.
     */
    private static final String ENTRY_DISMISS = "click[target.id=='" + ENTRY_MODAL + "'], "
            + "keyup[key=='Escape'&&document.querySelector('#" + ENTRY_MODAL + " > .card')!=null] from:body";

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
     * The whole page, or for a boosted navigation or a reload ({@link Ui#boosted}) only what changes in it. That one
     * always goes as {@code 200}: htmx drops an error answer unswapped (a missing account's 404 would look like a dead
     * click); the whole page keeps the real status. A reload keeps the window where it is.
     */
    public static HTTPResponse page(HTTPRequest request, HTTPStatus status, Screen current, @Nullable Node months,
                                    Node content) {
        if (!Ui.boosted(request)) return Ui.html(status, document(current, months, content));
        val changed = Html.fragment(pageTitle(current), contentOf(current, content), monthSlot(request, months));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget("#" + CONTENT)
                .reswap(Ui.reload(request) ? IN_PLACE : INTO_CONTENT).response();
    }

    /**
     * Only what the month changes on a screen ({@link Ui#monthOnly}): {@code region} in place of {@code target} (its
     * id selector), the month navigation beside the menu, and the window where it is.
     */
    public static HTTPResponse region(HTTPRequest request, Node months, String target, Node region) {
        val changed = Html.fragment(region, monthSlot(request, months));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget(target).reswap(IN_PLACE).response();
    }

    private static String document(Screen current, @Nullable Node months, Node content) {
        return Html5.document(html(
                lang("pt-BR"),
                head(
                        metaCharset("utf-8"),
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
                                navigation(),
                                monthSlot(months, Html.fragment()),
                                themeSwitch()
                        ),
                        contentOf(current, content),
                        div(id(ENTRY_MODAL), cls("modal"), hxGet(ENTRY_EDITOR), hxTrigger(ENTRY_DISMISS),
                                hxSwap(HxSwap.INNER_HTML)),
                        footer(small(text(message("Dados em memória"))))
                )
        ));
    }

    private static Node pageTitle(Screen current) {
        return title(text(message(current.label() + " · br.money")));
    }

    /**
     * The content of the screen: its {@code data-screen} marks the menu's link (CSS), and it reloads itself on
     * {@link #CHANGED} from the URL the browser is on.
     */
    private static Node contentOf(Screen current, Node content) {
        return main(id(CONTENT), data("screen", current.name()), hxGet(""), hxTrigger(CHANGED + " from:body"), content);
    }

    /** The menu, the same on every screen: it goes once, with the page. */
    private static Node navigation() {
        return nav(
                cls("menu"),
                ariaLabel(message("Menu")),
                ul(each(List.of(Screen.values()), screen -> li(menuLink(screen))))
        );
    }

    /**
     * A link of the menu, with its screen ({@code data-screen}) for the CSS to mark it. The quick entry is not a
     * screen to go to: it opens as a dialog over the one the user is on ({@link #ENTRY_MODAL}); its {@code href}
     * stays for a browser without htmx.
     */
    private static Node menuLink(Screen screen) {
        val attributes = new ArrayList<Content>();
        attributes.add(href(screen.path()));
        attributes.add(data("screen", screen.name()));
        if (screen == Screen.ENTRY) {
            attributes.add(hxGet(screen.path()));
            attributes.add(hxTarget("#" + ENTRY_MODAL));
            attributes.add(hxSwap(HxSwap.INNER_HTML));
        }
        attributes.add(span(cls("icon"), ariaHidden(true), screen.icon().svg()));
        attributes.add(span(cls("label"), data("label", screen.label()), text(message(screen.label()))));
        return a(attributes.toArray(Content[]::new));
    }

    /**
     * Where the month navigation of the screen goes, beside the menu: empty on a screen without a month. Its key, a
     * hash of what it shows, goes in {@code data-key} for {@code month.js} to send back ({@link #MONTH_KEY}).
     */
    private static Node monthSlot(@Nullable Node months, Content oob) {
        val nav = months == null ? Html.fragment() : months;
        return div(id("month-slot"), cls("month-slot"), data("key", monthKey(nav)), oob, nav);
    }

    /**
     * Beside the content: the month navigation out of band when the page shows another one (its key differs), so the
     * previous screen's months do not stay; nothing when it is the same.
     */
    private static Node monthSlot(HTTPRequest request, @Nullable Node months) {
        val nav = months == null ? Html.fragment() : months;
        return monthKey(nav).equals(request.header(MONTH_KEY)) ? Html.fragment() : monthSlot(nav, hxSwapOob());
    }

    /** What the month navigation shows, in a few characters: the same navigation gives the same key. */
    private static String monthKey(Node months) {
        return Integer.toHexString(months.render().hashCode());
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
