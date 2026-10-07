package br.commons.lang.html.form;

import br.commons.Result;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.FieldSpec;
import br.commons.lang.html.form.InputType;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldTest {

    @Test
    void buildsFromTheSpec() {
        val spec = FieldSpec.of("n", InputType.NUMBER).asRequired().withMin("1").withMax("9").withStep("any").asMultiple();
        assertEquals(new FieldSpec("n", InputType.NUMBER, true, null, null, null, "1", "9", "any", true), spec);
        val field = spec.build().getOrThrow();
        assertEquals(spec, field.spec());
        assertEquals(spec.toString(), field.toString());
        assertEquals(spec.build().getOrThrow(), field);
        assertEquals(field.hashCode(), spec.build().getOrThrow().hashCode());
        assertNotEquals(FieldSpec.of("m", InputType.NUMBER).build().getOrThrow(), field);
        assertNull(field.stepSize());
    }

    @Test
    void keepsPatternAndBoundsRead() {
        val day = FieldSpec.of("d", InputType.DATE).withMin("2026-01-01").withMax("2026-12-31").build().getOrThrow();
        assertEquals(LocalDate.of(2026, 1, 1), day.lower());
        assertEquals(LocalDate.of(2026, 12, 31), day.upper());
        val text = FieldSpec.of("t", InputType.TEXT).withPattern("\\d+").build().getOrThrow();
        assertTrue(text.regex() != null && text.regex().matcher("12").matches());
    }

    @Test
    void failsInconsistentDeclarations() {
        val inconsistent = List.of(
                FieldSpec.of(" ", InputType.TEXT),
                FieldSpec.of("a", InputType.TEXT).withMinLength(-1),
                FieldSpec.of("a", InputType.TEXT).withMaxLength(-1),
                FieldSpec.of("a", InputType.TEXT).withMaxLength(2).withMinLength(3),
                FieldSpec.of("a", InputType.TEXT).withPattern("("),
                FieldSpec.of("a", InputType.TEXT).withMin("1"),
                FieldSpec.of("a", InputType.DATE).withMax("2026-13-01"),
                FieldSpec.of("a", InputType.DATE).withStep("1"),
                FieldSpec.of("a", InputType.NUMBER).withStep("0"),
                FieldSpec.of("a", InputType.NUMBER).withStep("x"));
        for (val spec : inconsistent) {
            assertTrue(spec.build() instanceof Result.Failure<Field, String>, spec.toString());
        }
        assertTrue(FieldSpec.of("a", InputType.TEXT).withMinLength(2).withMaxLength(2).build().isSuccess());
    }
}
