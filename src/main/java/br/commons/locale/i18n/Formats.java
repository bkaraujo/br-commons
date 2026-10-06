package br.commons.locale.i18n;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.util.Currency;
import java.util.Locale;

/**
 * Formatting in a locale, never with a fixed pattern in code: patterns come from CLDR skeletons
 * ({@link DateTimeFormatter#ofLocalizedPattern}). Money is written in its own currency ({@link CurrencyAmount#currency()},
 * BRL by default) with the symbols and grouping of the locale.
 * In {@code pt-BR}: {@code R$ 1.234,56}, {@code 30/09/2026}, {@code setembro de 2026}, {@code 82%}.
 */
@NullMarked
public final class Formats {

    private final Locale locale;
    private final DateTimeFormatter dayMonthYear;
    private final DateTimeFormatter monthLong;
    private final DateTimeFormatter monthShort;
    private final DateTimeFormatter dayMonth;

    public Formats(Locale locale) {
        this.locale = locale;
        this.dayMonthYear = DateTimeFormatter.ofLocalizedPattern("yMd").withLocale(locale);
        this.monthLong = DateTimeFormatter.ofLocalizedPattern("yMMMM").withLocale(locale);
        this.monthShort = DateTimeFormatter.ofLocalizedPattern("yMMM").withLocale(locale);
        // The locale's day + short month pattern with a two-digit day, like Intl's {day: '2-digit', month: 'short'}.
        this.dayMonth = DateTimeFormatter.ofPattern(DateTimeFormatterBuilder
                .getLocalizedDateTimePattern("MMMd", IsoChronology.INSTANCE, locale)
                .replaceAll("(?<!d)d(?!d)", "dd"), locale);
    }

    public Locale locale() {
        return locale;
    }

    /** {@code amount} in {@code currency}, written the way the locale writes money ({@code R$ 1.234,56}). */
    public String money(BigDecimal amount, Currency currency) {
        val format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        return format.format(amount);
    }

    /** {@link #money} in {@link CurrencyAmount#BRL}. */
    public String brl(BigDecimal amount) {
        return money(amount, CurrencyAmount.BRL);
    }

    public String number(Number value) {
        return NumberFormat.getNumberInstance(locale).format(value);
    }

    /** A share (0.82) as a percentage (82%), with at most one decimal place. */
    public String percent(BigDecimal share) {
        val format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(1);
        return format.format(share);
    }

    public String dayMonthYear(LocalDate date) {
        return dayMonthYear.format(date);
    }

    public String monthLong(YearMonth month) {
        return monthLong.format(month);
    }

    public String monthShort(YearMonth month) {
        return monthShort.format(month);
    }

    /** Day and short month ("12 de set." in pt-BR), the date of a statement row. */
    public String dayMonth(LocalDate date) {
        return dayMonth.format(date);
    }

    /** The month's standalone name, capitalized ("Setembro"), for month pickers. */
    public String monthName(Month month) {
        val name = month.getDisplayName(TextStyle.FULL_STANDALONE, locale);
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** Default formatting of a bundle argument by its type (spec §2.5). */
    public String argument(Object value) {
        return switch (value) {
            case Text text -> text.value();
            case CurrencyAmount money -> money(money.amount(), money.currency());
            case LocalDate date -> dayMonthYear(date);
            case DayMonth date -> dayMonth(date.date());
            case YearMonth month -> monthLong(month);
            case Number number -> number(number);
            default -> value.toString();
        };
    }
}
