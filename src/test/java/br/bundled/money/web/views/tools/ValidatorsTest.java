package br.bundled.money.web.views.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A value in reais as {@code money.js} formats it, or as typed without JavaScript. */
class ValidatorsTest {

    @Test
    void readsTheFormatOfTheScreen() {
        assertEquals(new BigDecimal("1234.56"), Validators.money("1.234,56"));
        assertEquals(new BigDecimal("1234567.89"), Validators.money("1.234.567,89"));
        assertEquals(new BigDecimal("0.01"), Validators.money("0,01"));
        assertEquals(new BigDecimal("12.00"), Validators.money(" R$ 12,00 "));
    }

    @Test
    void readsWhatIsTypedWithoutJavaScript() {
        assertEquals(new BigDecimal("1234.50"), Validators.money("1234,5"));
        assertEquals(new BigDecimal("1234.50"), Validators.money("1234.50"));
        assertEquals(new BigDecimal("7.00"), Validators.money("7"));
    }

    @Test
    void signedAllowsALeadingMinus() {
        assertEquals(new BigDecimal("-1234.56"), Validators.signed("-1.234,56"));
        assertEquals(new BigDecimal("12.00"), Validators.signed("12"));
        assertNull(Validators.signed("--5"));
        assertNull(Validators.signed("-"));
        assertNull(Validators.signed("abc"));
    }

    @Test
    void refusesAnythingElse() {
        assertNull(Validators.money(""));
        assertNull(Validators.money("abc"));
        assertNull(Validators.money("12,345"));
        assertNull(Validators.money("-5,00"));
        assertNull(Validators.money("1,2,3"));
    }
}
