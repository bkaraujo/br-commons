package br.commons.locale.i18n;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TextAndFormatsTest {

    private final Formats fmt = new Formats(Locale.of("pt", "BR"));

    @Test
    void dataTextKeepsTheValueAndItsKind() {
        val data = Text.data("PADARIA");
        assertTrue(data.isData());
        assertEquals("PADARIA", data.value());
        assertEquals("PADARIA", data.toString());
        assertEquals(Text.data("PADARIA"), data);
        assertEquals(Text.data("PADARIA").hashCode(), data.hashCode());
        assertNotEquals(Text.message("PADARIA"), data);
        assertNotEquals(data, "PADARIA");
    }

    @Test
    void formatsInTheLocale() {
        assertEquals("R$ 1.234,56", fmt.brl(new BigDecimal("1234.56")));
        assertEquals("-R$ 12,30", fmt.brl(new BigDecimal("-12.30")));
        assertEquals("82%", fmt.percent(new BigDecimal("0.82")));
        assertEquals("12,5%", fmt.percent(new BigDecimal("0.125")));
        assertEquals("30/09/2026", fmt.dayMonthYear(LocalDate.of(2026, 9, 30)));
        assertEquals("setembro de 2026", fmt.monthLong(YearMonth.of(2026, 9)));
        assertEquals("set. de 2026", fmt.monthShort(YearMonth.of(2026, 9)));
        assertEquals("1.234,5", fmt.number(new BigDecimal("1234.5")));
        assertEquals(Locale.of("pt", "BR"), fmt.locale());
    }

    /** The currency comes from the value: dollars in pt-BR are still dollars, written the pt-BR way. */
    @Test
    void formatsMoneyInItsOwnCurrency() {
        val usd = java.util.Currency.getInstance("USD");
        CurrencyAmount dollars = new CurrencyAmount() {
            @Override public BigDecimal amount() { return new BigDecimal("1234.5"); }
            @Override public java.util.Currency currency() { return usd; }
        };
        val written = fmt.argument(dollars);
        assertEquals(fmt.money(new BigDecimal("1234.5"), usd), written);
        assertTrue(written.startsWith("US$") && written.endsWith("1.234,50"), written);
        assertEquals(fmt.brl(BigDecimal.TEN), fmt.argument((CurrencyAmount) () -> BigDecimal.TEN));
    }

    @Test
    void formatsArgumentsByType() {
        assertEquals("x", fmt.argument(Text.data("x")));
        assertEquals("true", fmt.argument(Boolean.TRUE));
        assertEquals("7", fmt.argument(7));
    }
}
