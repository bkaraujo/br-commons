package br.commons.i18n;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BundlesTest {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private final TestMessages msg = Bundles.of(TestMessages.class, "messages/test", PT_BR);

    @Test
    void substitutesParametersByNameAndFormatsByType() {
        val text = msg.greeting("Maria", LocalDate.of(2026, 9, 30));
        assertEquals("Olá, Maria! Hoje é 30/09/2026.", text.value());
        assertFalse(text.isData());
    }

    @Test
    void formatsMoneyMonthAndNumbers() {
        CurrencyAmount amount = () -> new BigDecimal("1234.56");
        val text = msg.total(amount, YearMonth.of(2026, 9), new BigDecimal("1200"));
        assertEquals("Total de R$ 1.234,56 em setembro de 2026 (1.200 itens)", text.value());
    }

    @Test
    void keepsTheApostropheAsWritten() {
        assertEquals("D'Ávila d'água", msg.apostrophe().value());
    }

    @Test
    void readsEnumLabelsFromMethodAndConstant() {
        assertEquals("Vermelho", msg.color(TestMessages.Color.RED).value());
        assertEquals("Verde", msg.color(TestMessages.Color.GREEN).value());
    }

    @Test
    void answersObjectMethods() {
        assertTrue(msg.toString().contains("messages/test"));
        assertEquals(msg, msg);
        assertNotEquals(msg, Bundles.of(TestMessages.class, "messages/test", PT_BR));
        assertEquals(System.identityHashCode(msg), msg.hashCode());
    }

    @Test
    void completeBundleHasNoProblems() {
        assertEquals(List.of(), Bundles.check(TestMessages.class, "messages/test", PT_BR));
    }

    @Test
    void reportsEveryProblemOfAnIncompleteBundle() {
        val problems = Bundles.check(BrokenMessages.class, "messages/broken", PT_BR);
        assertTrue(problems.contains("greeting: {nome} is not a parameter"), problems::toString);
        assertTrue(problems.contains("missing key missing"), problems::toString);
        assertTrue(problems.contains("notText must return Text"), problems::toString);
        assertTrue(problems.contains("orphan key orphan"), problems::toString);
    }

    @Test
    void refusesToCreateAnIncompleteBundle() {
        assertThrows(IllegalStateException.class, () -> Bundles.of(BrokenMessages.class, "messages/broken", PT_BR));
    }
}
