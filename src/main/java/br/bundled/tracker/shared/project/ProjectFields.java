package br.bundled.tracker.shared.project;

import br.commons.Result;
import br.bundled.tracker.shared.registry.Registry;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The fields of a project; {@link #read} also checks that the name is not taken by another project. */
@NullMarked
public final class ProjectFields {

    public static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    public static final Field CLIENT = FieldSpec.of("client", InputType.TEXT).withMaxLength(60).build().getOrThrow();

    private ProjectFields() {}

    /** The checked values, or the violations; {@code self} is the project being edited (0 for a new one). */
    public static Result<ProjectInput, List<Violation>> read(Function<String, String> value, Registry<Project> projects, int self) {
        val violations = Validation.check(List.of(NAME, CLIENT), name -> List.of(value.apply(name)));
        if (!violations.isEmpty()) {
            return new Result.Failure<>(violations);
        }
        val input = new ProjectInput(value.apply(NAME.name()).strip(), value.apply(CLIENT.name()).strip());
        val taken = projects.all().stream()
                .anyMatch(project -> project.id() != self && project.name().equalsIgnoreCase(input.name()));
        return taken
                ? new Result.Failure<>(List.of(new Violation(NAME.name(), ValidationRule.INVALID)))
                : Result.success(input);
    }

    public static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case INVALID -> "Já existe um projeto com este nome.";
            default -> "Valor inválido.";
        };
    }

    public static Map<String, String> values(Project project) {
        return Map.of(NAME.name(), project.name(), CLIENT.name(), project.client());
    }

    public static Map<String, String> values(Function<String, String> value) {
        return Map.of(NAME.name(), value.apply(NAME.name()), CLIENT.name(), value.apply(CLIENT.name()));
    }
}
