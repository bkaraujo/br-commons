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
import br.commons.web.server.WebRequest;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
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
     * Whether to answer with a fragment: htmx asked, and it is neither a boosted navigation (which gets the content
     * from {@link Layout#page}) nor a history restore (which needs the whole page).
     */
    public static boolean partial(HTTPRequest request) {
        val htmx = HtmxRequest.of(request);
        return htmx.isHtmx() && !htmx.boosted() && !htmx.historyRestore();
    }

    /** A boosted navigation (not a history restore, which swaps the whole body): it gets only the content. */
    public static boolean boosted(HTTPRequest request) {
        val htmx = HtmxRequest.of(request);
        return htmx.boosted() && !htmx.historyRestore();
    }

    public static HTTPResponse ok(String html) {
        return html(HTTPStatus.OK, html);
    }

    /**
     * Every HTML answer: the same URL gives the page, a boosted navigation's content or a fragment, by the htmx
     * headers; {@code Vary} keeps a cache from serving one for the other.
     */
    static HTTPResponse html(HTTPStatus status, String html) {
        return WebResponse.html(status, html).header("Vary", "HX-Request, HX-Boosted");
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
