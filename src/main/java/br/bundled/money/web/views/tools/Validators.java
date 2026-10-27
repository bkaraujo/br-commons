package br.bundled.money.web.views.tools;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.ValidationRule;
import br.commons.lang.html.form.Violation;
import br.commons.web.server.WebRequest;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * What checks an input before the screens use it: ids, positions, months, values in reais and the fields that depend
 * on the book. A reading gives {@code null} for anything invalid; a check adds the {@link Violation} of its field.
 */
@NullMarked
public abstract class Validators {

    private static final Pattern MONEY = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?");

    /** The months a URL may ask for: the month select lists every one between the book's and the one asked. */
    private static final YearMonth FIRST_MONTH = YearMonth.of(1900, 1);
    private static final YearMonth LAST_MONTH = YearMonth.of(2100, 12);

    private Validators() {}

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

    /**
     * A value typed in reais, as {@code money.js} shows it ({@code 1.234,56}) or as typed without JavaScript
     * ({@code 1234,5}, {@code 1234.50}): with a comma, the dots group thousands; without one, a dot is the decimal
     * point. Two decimal places at most; {@code null} for anything else.
     */
    public static @Nullable BigDecimal money(String text) {
        val plain = text.replace("R$", "").strip();
        val decimal = plain.contains(",") ? plain.replace(".", "").replace(',', '.') : plain;
        return MONEY.matcher(decimal).matches() ? new BigDecimal(decimal).setScale(2, RoundingMode.UNNECESSARY) : null;
    }

    /** {@link #money} with an optional leading minus sign ({@code -1.234,56}); {@code null} for anything else. */
    public static @Nullable BigDecimal signed(String text) {
        val plain = text.strip();
        val negative = plain.startsWith("-");
        val value = money(negative ? plain.substring(1) : plain);
        return value == null ? null : negative ? value.negate() : value;
    }

    /** {@link #money} above zero; {@code null} for anything else. */
    public static @Nullable BigDecimal positive(String text) {
        val value = money(text);
        return value != null && value.signum() > 0 ? value : null;
    }

    /** The amount of {@code field} typed, above zero; {@code null} (with its violation) when it is not. */
    public static @Nullable BigDecimal amount(Field field, Function<String, String> value, List<Violation> violations) {
        val amount = money(value.apply(field.name()));
        if (amount == null) violations.add(new Violation(field.name(), ValidationRule.INVALID));
        else if (amount.signum() <= 0) violations.add(new Violation(field.name(), ValidationRule.BELOW_MIN));
        return amount;
    }

    /** A date before the closing date is a violation of {@code field}: the period is immutable. */
    public static void open(Field field, LocalDate date, Book book, List<Violation> violations) {
        if (book.closed(date)) violations.add(new Violation(field.name(), ValidationRule.INVALID));
    }

    /** The account chosen in {@code field} when it is {@code usable}; otherwise a violation and {@code null}. */
    public static @Nullable Account account(Field field, Function<String, String> value, Book book, Predicate<Account> usable, List<Violation> violations) {
        val found = book.account(uuid(value.apply(field.name()))).filter(usable);
        if (found.isPresent()) return found.get();
        violations.add(new Violation(field.name(), ValidationRule.INVALID));
        return null;
    }
}
