package br.commons.examples.htmx.tracker.person;

import br.commons.Result;
import br.commons.examples.htmx.tracker.registry.Registry;
import br.commons.html.form.Field;
import br.commons.html.form.FieldSpec;
import br.commons.html.form.InputType;
import br.commons.html.form.Validation;
import br.commons.html.form.ValidationRule;
import br.commons.html.form.Violation;
import br.commons.web.server.Form;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/** The fields of a person; {@link #read} also checks that the e-mail is not taken by another person. */
@NullMarked
final class PersonFields {

    static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    static final Field EMAIL = FieldSpec.of("email", InputType.EMAIL).asRequired().withMaxLength(120)
            .build().getOrThrow();

    private PersonFields() {}

    /** The checked values, or the violations; {@code self} is the person being edited (0 for a new one). */
    static Result<PersonInput, List<Violation>> read(Form form, Registry<Person> people, int self) {
        val violations = Validation.check(List.of(NAME, EMAIL), form::all);
        if (!violations.isEmpty()) {
            return new Result.Failure<>(violations);
        }
        val input = new PersonInput(form.value(NAME.name()).strip(), form.value(EMAIL.name()).strip());
        val taken = people.all().stream()
                .anyMatch(person -> person.id() != self && person.email().equalsIgnoreCase(input.email()));
        return taken
                ? new Result.Failure<>(List.of(new Violation(EMAIL.name(), ValidationRule.PATTERN)))
                : Result.success(input);
    }

    /** {@code PATTERN} stands for an e-mail taken by someone else; {@code INVALID}, for a malformed one. */
    static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case INVALID -> "E-mail inválido.";
            case PATTERN -> "Já existe uma pessoa com este e-mail.";
            default -> "Valor inválido.";
        };
    }

    static Map<String, String> values(Person person) {
        return Map.of(NAME.name(), person.name(), EMAIL.name(), person.email());
    }

    static Map<String, String> values(Form form) {
        return Map.of(NAME.name(), form.value(NAME.name()), EMAIL.name(), form.value(EMAIL.name()));
    }
}
