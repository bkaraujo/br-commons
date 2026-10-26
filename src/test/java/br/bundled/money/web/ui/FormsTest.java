package br.bundled.money.web.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A value in reais as {@code money.js} formats it, or as typed without JavaScript. */
class FormsTest {

    @Test
    void readsTheFormatOfTheScreen() {
        assertEquals(new BigDecimal("1234.56"), Forms.money("1.234,56"));
        assertEquals(new BigDecimal("1234567.89"), Forms.money("1.234.567,89"));
        assertEquals(new BigDecimal("0.01"), Forms.money("0,01"));
        assertEquals(new BigDecimal("12.00"), Forms.money(" R$ 12,00 "));
    }

    @Test
    void readsWhatIsTypedWithoutJavaScript() {
        assertEquals(new BigDecimal("1234.50"), Forms.money("1234,5"));
        assertEquals(new BigDecimal("1234.50"), Forms.money("1234.50"));
        assertEquals(new BigDecimal("7.00"), Forms.money("7"));
    }

    @Test
    void refusesAnythingElse() {
        assertNull(Forms.money(""));
        assertNull(Forms.money("abc"));
        assertNull(Forms.money("12,345"));
        assertNull(Forms.money("-5,00"));
        assertNull(Forms.money("1,2,3"));
    }
}
