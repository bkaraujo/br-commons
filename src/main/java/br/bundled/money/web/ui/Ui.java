package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.AccountType;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static br.commons.lang.Html5.*;

/**
 * What the screens share: money, date and month formats, the error next to a field and request
 * helpers. Names avoid the DSL ({@code pathId}, not {@code id}): a method declared here would hide the statically
 * imported ones.
 */
@NullMarked
public final class Ui {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BR);
    private static final DateTimeFormatter SHORT_MONTH = DateTimeFormatter.ofPattern("MMM/yy", BR);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", BR);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", BR);

    /** The months a URL may ask for: the month select lists every one between the book's and the one asked. */
    private static final YearMonth FIRST_MONTH = YearMonth.of(1900, 1);
    private static final YearMonth LAST_MONTH = YearMonth.of(2100, 12);

    private Ui() {}

    public static Text message(String text) {
        return Text.message(text);
    }

    /** {@code R$ 1.234,56}; negative with a minus sign. */
    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BR).format(value);
    }

    /** The value in a {@code data} element, colored by its sign ({@code .neg}, {@code .pos} when asked). */
    public static Node amount(BigDecimal value, boolean signed) {
        val sign = value.signum() < 0 ? " neg" : signed && value.signum() > 0 ? " pos" : "";
        val shown = signed && value.signum() > 0 ? "+" + money(value) : money(value);
        return data(value(value.toPlainString()), cls("money" + sign), text(Text.data(shown)));
    }

    /** {@code outubro de 2026}. */
    public static String month(YearMonth month) {
        return MONTH.format(month);
    }

    /** {@code out./26}. */
    public static String shortMonth(YearMonth month) {
        return SHORT_MONTH.format(month);
    }

    /** {@code 20/10}. */
    public static String day(LocalDate date) {
        return DAY.format(date);
    }

    /** {@code 20/10/2026}. */
    public static String fullDay(LocalDate date) {
        return FULL_DAY.format(date);
    }

    /**
     * The month of the query string ({@code 2026-10}), or {@code fallback} when absent, invalid or out of range:
     * computed only then (it may read the whole book).
     */
    public static YearMonth monthOf(@Nullable String value, Supplier<YearMonth> fallback) {
        if (value == null || value.isBlank()) return fallback.get();
        try {
            val month = YearMonth.parse(value);
            return month.isBefore(FIRST_MONTH) || month.isAfter(LAST_MONTH) ? fallback.get() : month;
        } catch (DateTimeParseException ignored) {
            return fallback.get();
        }
    }

    public static String typeLabel(AccountType type) {
        return TYPE_LABELS.getOrDefault(type, type.name());
    }

    /** A lookup, not a switch: one label per type would exceed the complexity gate (PMD). */
    private static final Map<AccountType, String> TYPE_LABELS = new EnumMap<>(Map.ofEntries(
            Map.entry(AccountType.BANK, "Banco"),
            Map.entry(AccountType.CASH, "Dinheiro"),
            Map.entry(AccountType.CREDIT, "Cartão de crédito"),
            Map.entry(AccountType.STOCK, "Ações"),
            Map.entry(AccountType.MUTUAL, "Fundos"),
            Map.entry(AccountType.ASSET, "Ativo"),
            Map.entry(AccountType.LIABILITY, "Passivo"),
            Map.entry(AccountType.EQUITY, "Patrimônio líquido"),
            Map.entry(AccountType.INCOME, "Receita"),
            Map.entry(AccountType.EXPENSE, "Despesa"),
            Map.entry(AccountType.ROOT, "Raiz"),
            Map.entry(AccountType.RECEIVABLE, "A receber"),
            Map.entry(AccountType.PAYABLE, "A pagar"),
            Map.entry(AccountType.TRADING, "Câmbio")));

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
        return WebResponse.html(status, html)
                .header("Vary", HtmxRequest.VARY + ", HX-Trigger, HX-Current-URL, " + Layout.MONTH_KEY);
    }

    /** The {@code {id}} of the route; {@code null} (matches nothing) when it is not an id. */
    public static @Nullable UUID pathId(WebRequest request) {
        return uuid(request.pathParam("id").getOrElse(""));
    }

    /** An id, or {@code null} for anything else (absent, empty, not an id). */
    public static @Nullable UUID uuid(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** A position (zero or more), or {@code null} for anything else (absent, empty, negative, not a number). */
    public static @Nullable Integer number(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            val number = Integer.parseInt(value.strip());
            return number >= 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
