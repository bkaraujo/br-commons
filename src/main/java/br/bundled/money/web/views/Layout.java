package br.bundled.money.web.views;

import br.bundled.money.web.ui.Ledger;
import br.commons.Strings;
import br.commons.lang.Html5;
import br.commons.lang.Json;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.bundled.money.web.views.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The page around every screen: head, the menu and the theme switch. The body is boosted ({@code hx-boost}): every
 * link and form loads the next page by htmx and pushes the URL, without a full reload. The header stays fixed on top
 * and a boosted navigation swaps only {@code <main id="content">}: the server answers it with the title, the content
 * (retargeted there) and, out of band, the month navigation when it changed ({@link #MONTH_KEY}), so the scripts in
 * the {@code <head>} load once. The menu never comes back: the whole page marks the link of its screen
 * ({@code aria-current}) and, after a navigation, {@code static/money/header.js} marks the one of the content's
 * {@code data-screen}. A change made over any screen ({@link #CHANGED}) reloads the content from the URL the browser
 * is on. The header's real height goes to {@code --top-height}
 * ({@code static/money/header.js}); the month navigation of a screen with a month sits beside the menu
 * ({@code static/money/month.js}). Every {@code <select>} gets a live filter in the browser
 * ({@code static/money/combo.js}); the home's chart is drawn from its table ({@code static/money/chart.js}); the form of splits sums its
 * columns while typing ({@code static/money/splits.js}); the rows of an outline open and close at any level
 * ({@code static/money/outline.js}); a value in reais is formatted while typed ({@code static/money/money.js}); the checkboxes go to the history as they are ({@code static/money/history.js}).
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
    public static HTTPResponse page(HTTPRequest request, Screen current, Ledger.MonthNav months, Node content) {
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
    public static HTTPResponse page(HTTPRequest request, HTTPStatus status, Screen current,
                                    Ledger.@Nullable MonthNav months, Node content) {
        if (!Ui.boosted(request)) return Ui.html(status, document(current, months, content));
        val changed = Html.fragment(pageTitle(current), contentOf(current, content), monthSlot(request, months));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget("#" + CONTENT)
                .reswap(Ui.reload(request) ? IN_PLACE : INTO_CONTENT).response();
    }

    /**
     * Only what the month changes on a screen ({@link Ui#monthOnly}): {@code region} in place of {@code target} (its
     * id selector), the month navigation beside the menu, and the window where it is.
     */
    public static HTTPResponse region(HTTPRequest request, Ledger.MonthNav months, String target, Node region) {
        val changed = Html.fragment(region, monthSlot(request, months));
        return HtmxResponse.of(Ui.ok(changed.render())).retarget(target).reswap(IN_PLACE).response();
    }

    /**
     * The month navigation beside the menu, out of band, for an answer that changes the month a screen shows (a write
     * on it); nothing when the page already shows that one.
     */
    public static Node monthChanged(HTTPRequest request, Ledger.MonthNav months) {
        return monthSlot(request, months);
    }

    private static String document(Screen current, Ledger.@Nullable MonthNav months, Node content) {
        return Html5.document(html(
                lang(L10N.locale().toLanguageTag()),
                data("texts", scriptTexts()),
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
                        script(src("/static/money/splits.js"), defer()),
                        script(src("/static/money/money.js"), defer()),
                        script(src("/static/money/history.js"), defer())
                ),
                body(
                        hxBoost(true),
                        header(
                                cls("top"),
                                p(cls("brand"), span(cls("logo"), text(Text.data("R$"))), text(Text.data("br.money"))),
                                navigation(current),
                                monthSlot(months, Html.fragment()),
                                themeSwitch()
                        ),
                        contentOf(current, content),
                        div(id(ENTRY_MODAL), cls("modal"), hxGet(ENTRY_EDITOR), hxTrigger(ENTRY_DISMISS),
                                hxSwap(HxSwap.INNER_HTML)),
                        footer(small(text(message("forms.layout.in_memory"))))
                )
        ));
    }

    /**
     * The texts the scripts write in the browser ({@code combo.js}, {@code chart.js}, {@code splits.js}), from the
     * bundle, as JSON in {@code <html data-texts>}: the scripts never hold interface text; {@code %s} is theirs to fill.
     */
    private static String scriptTexts() {
        return Json.write(Map.of(
                "noResults", Ui.string("forms.script.no_results"),
                "chooseFromList", Ui.string("forms.script.choose_from_list"),
                "income", Ui.string("forms.home.income"),
                "expenses", Ui.string("forms.home.expenses"),
                "balanced", Ui.string("forms.statement.balanced"),
                "difference", Ui.string("forms.statement.difference")));
    }

    private static Node pageTitle(Screen current) {
        return title(text(message("forms.layout.title", current.label())));
    }

    /**
     * The content of the screen: its {@code data-screen} marks the menu's link after a navigation
     * ({@code header.js}), and it reloads itself on {@link #CHANGED} from the URL the browser is on.
     */
    private static Node contentOf(Screen current, Node content) {
        return main(id(CONTENT), data("screen", current.name()), hxGet(""), hxTrigger(CHANGED + " from:body"), content);
    }

    /** The menu, the same on every screen: it goes once, with the page, the link of {@code current} marked. */
    private static Node navigation(Screen current) {
        return nav(
                cls("menu"),
                ariaLabel(message("forms.layout.menu")),
                ul(each(List.of(Screen.values()), screen -> li(menuLink(screen, screen == current))))
        );
    }

    /**
     * A link of the menu, with its screen ({@code data-screen}) for {@code header.js} to mark it. The quick entry is
     * not a screen to go to: it opens as a dialog over the one the user is on ({@link #ENTRY_MODAL}); its
     * {@code href} stays for a browser without htmx.
     */
    private static Node menuLink(Screen screen, boolean current) {
        val attributes = new ArrayList<Content>();
        attributes.add(href(screen.path()));
        attributes.add(data("screen", screen.name()));
        if (current) attributes.add(ariaCurrent("page"));
        if (screen == Screen.ENTRY) {
            attributes.add(hxGet(screen.path()));
            attributes.add(hxTarget("#" + ENTRY_MODAL));
            attributes.add(hxSwap(HxSwap.INNER_HTML));
        }
        attributes.add(span(cls("icon"), ariaHidden(true), screen.icon().svg()));
        attributes.add(span(cls("label"), data("label", screen.label()), text(Text.message(screen.label()))));
        return a(attributes.toArray(Content[]::new));
    }

    /**
     * Where the month navigation of the screen goes, beside the menu: empty on a screen without a month. Its key
     * ({@link Ledger.MonthNav#key}) goes in {@code data-key} for {@code month.js} to send back ({@link #MONTH_KEY}).
     */
    private static Node monthSlot(Ledger.@Nullable MonthNav months, Content oob) {
        return div(id("month-slot"), cls("month-slot"), data("key", monthKey(months)), oob,
                months == null ? Html.fragment() : months.node());
    }

    /**
     * Beside the content: the month navigation out of band when the page shows another one (its key differs), so the
     * previous screen's months do not stay; nothing when it is the same, and then it is not even built.
     */
    private static Node monthSlot(HTTPRequest request, Ledger.@Nullable MonthNav months) {
        return monthKey(months).equals(request.header(MONTH_KEY)) ? Html.fragment() : monthSlot(months, hxSwapOob());
    }

    /** The key of the navigation; an empty one on a screen without a month. */
    private static String monthKey(Ledger.@Nullable MonthNav months) {
        return months == null ? "" : months.key();
    }

    /**
     * The theme switch, all in the browser: {@code theme.js} applies the choice and marks the pressed button. It is
     * born {@code hidden} and the script reveals it, so without JavaScript there are no dead buttons.
     */
    private static Node themeSwitch() {
        return div(
                cls("theme"), hidden(), role("group"), ariaLabel(message("forms.layout.theme")),
                each(
                        List.of(Theme.values()), theme -> button(type(ButtonType.BUTTON), data("theme-choice", theme.token()),
                        title(message("forms.layout.theme_choice", Strings.lower(theme.label()))),
                        ariaLabel(message("forms.layout.theme_choice", theme.label())), theme.icon().svg())
                )
        );
    }
}
