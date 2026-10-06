package br.commons.html;

import br.commons.html.form.Field;
import br.commons.html.form.FieldSpec;
import br.commons.html.form.InputType;
import br.commons.html.form.Option;
import br.commons.html.html4.Html4;
import br.commons.html.html4.Html4Dialect;
import br.commons.html.html5.Html5;
import br.commons.html.html5.Html5Dialect;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static br.commons.html.Html.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The same fields rendered by each version, through its dialect strategy. */
class FormsTest {

    private static final Field CPF = FieldSpec.of("cpf", InputType.TEXT).asRequired().withMinLength(11).withMaxLength(14)
            .withPattern("[0-9.-]+").build().getOrThrow();
    private static final Field DAY = FieldSpec.of("day", InputType.DATE).withMin("2026-01-01").withMax("2026-12-31").build().getOrThrow();
    private static final Field AMOUNT = FieldSpec.of("amount", InputType.NUMBER).withMin("0").withStep("0.01").build().getOrThrow();
    private static final Field EMAILS = FieldSpec.of("to", InputType.EMAIL).asMultiple().withMaxLength(200).build().getOrThrow();

    @Test
    void html5RendersNativeTypesAndBrowserChecks() {
        assertEquals("""
                <input type="text" name="cpf" required minlength="11" maxlength="14" pattern="[0-9.-]+" value="123" id="cpf">\
                """, input(Html5.DIALECT, CPF, "123", id("cpf")).render());
        assertEquals("<input type=\"date\" name=\"day\" min=\"2026-01-01\" max=\"2026-12-31\">",
                input(Html5.DIALECT, DAY, "").render());
        assertEquals("<input type=\"number\" name=\"amount\" min=\"0\" step=\"0.01\" value=\"1.5\">",
                input(Html5.DIALECT, AMOUNT, "1.5").render());
        assertEquals("<input type=\"email\" name=\"to\" maxlength=\"200\" multiple>", input(Html5.DIALECT, EMAILS, "").render());
    }

    @Test
    void html4FallsBackToTextAndLeavesChecksToTheServer() {
        assertEquals("<input type=\"text\" name=\"cpf\" maxlength=\"14\" value=\"123\">", input(Html4.DIALECT, CPF, "123").render());
        assertEquals("<input type=\"text\" name=\"day\" value=\"2026-10-02\">", input(Html4.DIALECT, DAY, "2026-10-02").render());
        assertEquals("<input type=\"text\" name=\"amount\">", input(Html4.DIALECT, AMOUNT, "").render());
        assertEquals("<input type=\"text\" name=\"to\" maxlength=\"200\">", input(Html4.DIALECT, EMAILS, "").render());
        assertEquals("<input type=\"hidden\" name=\"h\" value=\"x\">",
                input(Html4.DIALECT, FieldSpec.of("h", InputType.HIDDEN).build().getOrThrow(), "x").render());
    }

    @Test
    void neverEchoesPasswordOrFile() {
        val password = FieldSpec.of("password", InputType.PASSWORD).asRequired().build().getOrThrow();
        assertEquals("<input type=\"password\" name=\"password\" required>", input(Html5.DIALECT, password, "secret").render());
        assertEquals("<input type=\"password\" name=\"password\">", input(Html4.DIALECT, password, "secret").render());
        val files = FieldSpec.of("files", InputType.FILE).asMultiple().build().getOrThrow();
        assertEquals("<input type=\"file\" name=\"files\" multiple>", input(Html5.DIALECT, files, "a.pdf").render());
        assertEquals("<input type=\"file\" name=\"files\">", input(Html4.DIALECT, files, "a.pdf").render());
    }

    @Test
    void choicesTextareaAndSelect() {
        val terms = FieldSpec.of("terms", InputType.CHECKBOX).asRequired().build().getOrThrow();
        assertEquals("<input type=\"checkbox\" name=\"terms\" required value=\"yes\" checked>",
                choice(Html5.DIALECT, terms, "yes", true).render());
        assertEquals("<input type=\"radio\" name=\"k\" value=\"a\" id=\"k-a\">",
                choice(Html4.DIALECT, FieldSpec.of("k", InputType.RADIO).build().getOrThrow(), "a", false, id("k-a")).render());

        val note = FieldSpec.of("note", InputType.TEXT).asRequired().withMaxLength(500).withPattern("x").build().getOrThrow();
        assertEquals("<textarea name=\"note\" required maxlength=\"500\" rows=\"3\">\n&lt;b&gt;</textarea>",
                textarea(Html5.DIALECT, note, "<b>", rows(3)).render());
        assertEquals("<textarea name=\"note\">\n</textarea>", textarea(Html4.DIALECT, note, "").render());
        // Regression: the parser drops the newline right after <textarea>, so a value starting with one lost it.
        assertEquals("<textarea name=\"note\">\n\nsecond line</textarea>",
                textarea(Html4.DIALECT, note, "\nsecond line").render());

        val tags = FieldSpec.of("tag", InputType.TEXT).asMultiple().asRequired().build().getOrThrow();
        val options = List.of(new Option("a", Text.data("A")), new Option("b", Text.data("B")));
        assertEquals(Markup.flat("""
                <select name="tag" required multiple class="c">
                  <option value="a">A</option>
                  <option value="b" selected>B</option>
                </select>
                """), select(Html5.DIALECT, tags, options, Set.of("b"), cls("c")).render());
        assertEquals(Markup.flat("""
                <select name="tag" multiple>
                  <option value="a" selected>A</option>
                  <option value="b">B</option>
                </select>
                """), select(Html4.DIALECT, tags, options, Set.of("a")).render());
        assertEquals("<select name=\"one\"></select>",
                select(Html4.DIALECT, FieldSpec.of("one", InputType.TEXT).build().getOrThrow(), List.of(), Set.of()).render());
    }

    @Test
    void eachVersionIsBoundToItsDialect() {
        assertSame(Html4Dialect.INSTANCE, Html4.DIALECT);
        assertSame(Html5Dialect.INSTANCE, Html5.DIALECT);
        assertEquals(input(Html4.DIALECT, DAY, "").render(), input(Html4Dialect.INSTANCE, DAY, "").render());
    }
}
