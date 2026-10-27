package br.bundled.money.web.views;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.ui.Ledger;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.locale.i18n.I18N;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.server.Form;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static br.commons.lang.Html5.*;

/**
 * What the screens share: the error next to a field and request
 * helpers. Names avoid the DSL ({@code pathId}, not {@code id}): a method declared here would hide the statically
 * imported ones.
 */
@NullMarked
public final class Ui {

    private Ui() {}

    /** Key prefix of the interface texts: they resolve in {@link #BUNDLE}, as {@code money.} resolves in {@code messages}. */
    public static final String PREFIX = "forms.";

    /** The interface texts of the web, {@code forms_<locale>.properties} (pt_BR, en_US). */
    public static final String BUNDLE = "br/bundled/money/forms";

    static { I18N.register(PREFIX, BUNDLE); }

    /**
     * The interface text of {@code key} ({@link #BUNDLE}) in the locale of the process ({@code L10N}, pt-BR by
     * default), {@code args} filling its {@code %s} ({@link I18N#of}). Data shown as is goes by {@code Text.data}.
     */
    public static Text message(String key, Object... args) {
        return Text.message(string(key, args));
    }

    /** {@link #message} as a {@code String}: for a text passed along before it is shown. */
    public static String string(String key, Object... args) {
        return I18N.of(key, args);
    }

    /** The name of the type on the screens ({@code forms.account_type.<TYPE>}). */
    public static String typeLabel(AccountType type) {
        return string(PREFIX + "account_type." + type.name());
    }

    /** The message of the field's violation next to it, or nothing. */
    public static Node errorOf(Field field, List<Violation> errors, Function<Violation, String> messages) {
        return errors.stream()
                .filter(violation -> violation.field().equals(field.name()))
                .findFirst()
                .map(violation -> (Node) small(cls("error"), role("alert"), text(message(messages.apply(violation)))))
                .orElseGet(Html::fragment);
    }

    /**
     * Whether to answer with a fragment: htmx asked, and it is neither a boosted navigation nor the content reloading
     * itself (both get the content from {@link Layout#page}) nor a history restore (which needs the whole page).
     */
    public static boolean partial(HTTPRequest request) {
        return HtmxRequest.of(request).fragment() && !reload(request);
    }

    /**
     * A boosted navigation ({@link HtmxRequest#navigation}), or the content reloading itself ({@link #reload}): both
     * get only the content.
     */
    public static boolean boosted(HTTPRequest request) {
        return HtmxRequest.of(request).navigation() || reload(request);
    }

    /**
     * The content asking for itself again after a change over the screen ({@link Layout#CHANGED}): it is the element
     * that triggered the request.
     */
    public static boolean reload(HTTPRequest request) {
        return HtmxRequest.of(request).trigger().filter(Layout.CONTENT::equals).isPresent();
    }

    /**
     * The month navigation of the header ({@code month.js}): a boosted navigation to the page's own path that changes
     * the month and nothing else in its query. The screen may then answer only what the month changes
     * ({@link Layout#region}); with anything else changed, or the month not, it is a navigation like any other (the
     * menu's link to the screen the user is on).
     */
    public static boolean monthOnly(HTTPRequest request) {
        if (!HtmxRequest.of(request).navigation()) return false;
        val asked = request.uri();
        val month = parameter(asked, Ledger.MONTH);
        return !month.isEmpty() && shown(request).filter(page -> Objects.equals(page.getPath(), asked.getPath())
                && !month.equals(parameter(page, Ledger.MONTH)) && others(page).equals(others(asked))).isPresent();
    }

    /** A parameter of the page the browser is on ({@code HX-Current-URL}); an empty text when it has none. */
    public static String shown(HTTPRequest request, String name) {
        return shown(request).map(page -> parameter(page, name)).orElse("");
    }

    /** A parameter of the URL's query; an empty text when it has none. */
    private static String parameter(URI uri, String name) {
        val query = uri.getRawQuery();
        return query == null ? "" : Form.parse(query).value(name);
    }

    /** The URL of the page the browser is on, when htmx sent one that parses. */
    private static Optional<URI> shown(HTTPRequest request) {
        try {
            return HtmxRequest.of(request).currentUrl().map(URI::create);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** The parameters of the query but the month, as written: {@code month.js} keeps the others' text and order. */
    private static List<String> others(URI uri) {
        val query = uri.getRawQuery();
        return query == null ? List.of() : Arrays.stream(query.split("&"))
                .filter(pair -> !pair.isEmpty() && !pair.startsWith(Ledger.MONTH + "=")).toList();
    }

    public static HTTPResponse ok(String html) {
        return html(HTTPStatus.OK, html);
    }

    /**
     * Every HTML answer: the same URL gives the page, the content (boosted, reloaded or only what the month changes)
     * or a fragment, by the request headers; {@code Vary} keeps a cache from serving one for the other.
     */
    static HTTPResponse html(HTTPStatus status, String html) {
        return WebResponse.html(status, html).header("Vary", HtmxRequest.VARY + ", HX-Trigger, HX-Current-URL, " + Layout.MONTH_KEY);
    }
}
