package br.bundled.money.web.views;

import br.bundled.money.web.views.tools.Formatters;
import br.bundled.money.web.views.tools.Validators;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards what can be checked without a browser of the look of financial values (the rest is in
 * {@code visual/roteiro-valores.md}): fields show them as the extrato does and are aligned to the right.
 */
class ValueStyleTest {

    private static final Path MONEY = Path.of("src/main/java/br/bundled/money/web");

    @Test
    void fieldShowsTheValueAsTheExtratoDoes() {
        assertEquals("1.234,56", Formatters.decimal(new BigDecimal("1234.56")));
        assertEquals("0,50", Formatters.decimal(new BigDecimal("0.5")));
        assertTrue(Formatters.money(new BigDecimal("1234.56")).contains(Formatters.decimal(new BigDecimal("1234.56"))));
    }

    @Test
    void whatTheFieldShowsIsReadBack() {
        val value = new BigDecimal("1234.56");
        assertEquals(value, Validators.money(Formatters.decimal(value)));
    }

    @Test
    void numericFieldsAreAlignedToTheRight() throws IOException {
        val css = Files.readString(MONEY.resolve("static/app.css"));
        assertTrue(css.contains("input[inputmode=\"numeric\"]"));
        assertTrue(css.matches("(?s).*input\\[type=\"number\"][^{]*\\{[^}]*text-align:\\s*right.*"));
    }

    /** A financial field is text with {@code money.js}, not {@code type=number}: that one shows {@code 1234.56}. */
    @Test
    void financialFieldsUseTheMask() throws IOException {
        for (val file : new String[]{"views/statement/StatementFields.java", "views/statement/SplitFields.java",
                "views/entry/EntryFields.java"}) {
            assertFalse(Files.readString(MONEY.resolve(file)).contains("InputType.NUMBER"), file);
        }
        val views = Files.readString(MONEY.resolve("views/statement/StatementViews.java"));
        assertEquals(3, views.split("data\\(\"money\"", -1).length - 1, "valor, entra e sai");
    }
}
