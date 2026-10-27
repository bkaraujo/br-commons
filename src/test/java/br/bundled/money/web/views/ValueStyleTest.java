package br.bundled.money.web.views;

import br.bundled.money.web.views.tools.Formatters;
import br.bundled.money.web.views.tools.Validators;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import br.commons.web.filter.StaticFiles;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

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
        val css = Files.readString(MONEY.resolve("static/css/controls.css"));
        assertTrue(css.contains("input[inputmode=\"numeric\"]"));
        assertTrue(css.matches("(?s).*input\\[type=\"number\"][^{]*\\{[^}]*text-align:\\s*right.*"));
    }

    /** A financial field is text with {@code money.js}, not {@code type=number}: that one shows {@code 1234.56}. */
    @Test
    void financialFieldsUseTheMask() throws IOException {
        for (val file : new String[]{"views/statement/StatementFields.java", "views/statement/SplitFields.java",
                "views/entry/EntryFields.java", "views/account/AccountFields.java"}) {
            assertFalse(Files.readString(MONEY.resolve(file)).contains("InputType.NUMBER"), file);
        }
        val views = Files.readString(MONEY.resolve("views/statement/StatementViews.java"));
        assertEquals(3, views.split("data\\(\"money\"", -1).length - 1, "valor, entra e sai");
    }

    /** The stylesheet is split by screen but reaches the browser as one response, with every import inlined. */
    @Test
    void stylesheetIsSplitByScreenAndServedInOneResponse() throws IOException {
        val files = new StaticFiles("/static/money/", "br/bundled/money/web/static", null, Duration.ZERO);
        val request = new HTTPServerRequest("", HTTPMethod.GET, "/static/money/app.css", Map.of(), new byte[0]);
        val css = new String(files.apply(request, _ -> HTTPServerResponse.status(HTTPStatus.NOT_FOUND)).body(),
                StandardCharsets.UTF_8);
        assertFalse(css.contains("@import"));
        assertTrue(css.contains(".statement-accounts"), "css/screens/statement.css");
        assertTrue(css.contains(".amount-input"), "css/screens/entry.css");
        assertTrue(css.contains(".htmx-request"), "css/htmx.css");
        try (Stream<Path> screens = Files.list(MONEY.resolve("static/css/screens"))) {
            assertTrue(screens.count() >= 5);
        }
    }
}
