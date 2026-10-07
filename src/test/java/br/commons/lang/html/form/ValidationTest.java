package br.commons.lang.html.form;

import br.commons.lang.html.form.*;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationTest {

    private static Optional<ValidationRule> rule(FieldSpec spec, String... values) {
        return Validation.check(spec.build().getOrThrow(), List.of(values)).map(Violation::rule);
    }

    @Test
    void emptyFailsOnlyRequired() {
        val name = FieldSpec.of("name", InputType.TEXT).withMinLength(3);
        assertEquals(Optional.empty(), rule(name));
        assertEquals(Optional.empty(), rule(name, ""));
        assertEquals(Optional.of(ValidationRule.REQUIRED), rule(name.asRequired(), ""));
        assertEquals(Optional.of(ValidationRule.REQUIRED), rule(name.asRequired()));
    }

    @Test
    void checksLengthAndPattern() {
        val cpf = FieldSpec.of("cpf", InputType.TEXT).withMinLength(3).withMaxLength(5).withPattern("\\d+");
        assertEquals(Optional.of(ValidationRule.TOO_SHORT), rule(cpf, "12"));
        assertEquals(Optional.of(ValidationRule.TOO_LONG), rule(cpf, "123456"));
        assertEquals(Optional.of(ValidationRule.PATTERN), rule(cpf, "12a"));
        assertEquals(Optional.empty(), rule(cpf, "1234"));
    }

    @Test
    void checksTypeAndRange() {
        val day = FieldSpec.of("day", InputType.DATE).withMin("2026-01-01").withMax("2026-12-31");
        assertEquals(Optional.of(ValidationRule.INVALID), rule(day, "02/10/2026"));
        assertEquals(Optional.of(ValidationRule.BELOW_MIN), rule(day, "2025-12-31"));
        assertEquals(Optional.of(ValidationRule.ABOVE_MAX), rule(day, "2027-01-01"));
        assertEquals(Optional.empty(), rule(day, "2026-10-02"));

        val amount = FieldSpec.of("amount", InputType.NUMBER).withMin("0.5").withMax("10").withStep("0.25");
        assertEquals(Optional.of(ValidationRule.INVALID), rule(amount, "+1"));
        assertEquals(Optional.of(ValidationRule.STEP), rule(amount, "1.1"));
        assertEquals(Optional.of(ValidationRule.ABOVE_MAX), rule(amount, "10.25"));
        assertEquals(Optional.empty(), rule(amount, "9.75"));
        assertEquals(Optional.empty(), rule(FieldSpec.of("n", InputType.NUMBER).withStep("any"), "0.123"));
        assertEquals(Optional.of(ValidationRule.STEP), rule(FieldSpec.of("n", InputType.RANGE).withStep("2"), "3"));
    }

    @Test
    void checksEveryValueAndSplitsMultipleEmails() {
        val tags = FieldSpec.of("tag", InputType.TEXT).withMaxLength(2);
        assertEquals(Optional.of(ValidationRule.TOO_LONG), rule(tags, "ok", "long"));
        val to = FieldSpec.of("to", InputType.EMAIL).asMultiple();
        assertEquals(Optional.empty(), rule(to, "a@b.com, c@d.org"));
        assertEquals(Optional.of(ValidationRule.INVALID), rule(to, "a@b.com, nope"));
        assertEquals(Optional.of(ValidationRule.INVALID), rule(FieldSpec.of("to", InputType.EMAIL), "a@b.com, c@d.org"));
    }

    @Test
    void checksAFormByName() {
        val fields = List.of(FieldSpec.of("cpf", InputType.TEXT).asRequired(), FieldSpec.of("age", InputType.NUMBER).withMin("18"),
                FieldSpec.of("site", InputType.URL)).stream().map(spec -> spec.build().getOrThrow()).toList();
        val values = Map.of("age", List.of("17"), "site", List.of("https://br.dev"));
        assertEquals(List.of(new Violation("cpf", ValidationRule.REQUIRED), new Violation("age", ValidationRule.BELOW_MIN)),
                Validation.check(fields, name -> values.getOrDefault(name, List.of())));
    }
}
