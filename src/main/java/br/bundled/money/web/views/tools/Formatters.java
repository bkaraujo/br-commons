package br.bundled.money.web.views.tools;

import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static br.commons.lang.Html5.*;

/** How the screens show values: money, dates and months, always in the same way (see {@code visual/roteiro-valores.md}). */
@NullMarked
public abstract class Formatters {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BR);
    private static final DateTimeFormatter SHORT_MONTH = DateTimeFormatter.ofPattern("MMM/yy", BR);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", BR);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", BR);

    private Formatters() {}

    /** {@code R$ 1.234,56}; negative with a minus sign. */
    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BR).format(value);
    }

    /** {@code 1.234,56}: the value in the way a field shows it ({@code money.js}), without the currency. */
    public static String decimal(BigDecimal value) {
        val format = NumberFormat.getNumberInstance(BR);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
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
}
