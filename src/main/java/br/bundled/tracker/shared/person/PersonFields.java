package br.bundled.tracker.shared.person;

import br.commons.Result;
import br.bundled.tracker.shared.registry.Registry;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The fields of a person; {@link #read} also checks that the e-mail is not taken by another person. */
@NullMarked
public final class PersonFields {

    public static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    public static final Field EMAIL = FieldSpec.of("email", InputType.EMAIL).asRequired().withMaxLength(120)
            .build().getOrThrow();

    private PersonFields() {}

    /** The checked values, or the violations; {@code self} is the person being edited (0 for a new one). */
    public static Result<PersonInput, List<Violation>> read(Function<String, String> value, Registry<Person> people, int self) {
        val violations = Validation.check(List.of(NAME, EMAIL), name -> List.of(value.apply(name)));
        if (!violations.isEmpty()) {
            return new Result.Failure<>(violations);
        }
        val input = new PersonInput(value.apply(NAME.name()).strip(), value.apply(EMAIL.name()).strip());
        val taken = people.all().stream()
                .anyMatch(person -> person.id() != self && person.email().equalsIgnoreCase(input.email()));
        return taken
                ? new Result.Failure<>(List.of(new Violation(EMAIL.name(), ValidationRule.PATTERN)))
                : Result.success(input);
    }

    /** {@code PATTERN} stands for an e-mail taken by someone else; {@code INVALID}, for a malformed one. */
    public static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case INVALID -> "E-mail inválido.";
            case PATTERN -> "Já existe uma pessoa com este e-mail.";
            default -> "Valor inválido.";
        };
    }

    public static Map<String, String> values(Person person) {
        return Map.of(NAME.name(), person.name(), EMAIL.name(), person.email());
    }

    public static Map<String, String> values(Function<String, String> value) {
        return Map.of(NAME.name(), value.apply(NAME.name()), EMAIL.name(), value.apply(EMAIL.name()));
    }
}
