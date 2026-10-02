package br.commons.examples.htmx.tracker.project;

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

/** The fields of a project; {@link #read} also checks that the name is not taken by another project. */
@NullMarked
final class ProjectFields {

    static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    static final Field CLIENT = FieldSpec.of("client", InputType.TEXT).withMaxLength(60).build().getOrThrow();

    private ProjectFields() {}

    /** The checked values, or the violations; {@code self} is the project being edited (0 for a new one). */
    static Result<ProjectInput, List<Violation>> read(Form form, Registry<Project> projects, int self) {
        val violations = Validation.check(List.of(NAME, CLIENT), form::all);
        if (!violations.isEmpty()) {
            return new Result.Failure<>(violations);
        }
        val input = new ProjectInput(form.value(NAME.name()).strip(), form.value(CLIENT.name()).strip());
        val taken = projects.all().stream()
                .anyMatch(project -> project.id() != self && project.name().equalsIgnoreCase(input.name()));
        return taken
                ? new Result.Failure<>(List.of(new Violation(NAME.name(), ValidationRule.INVALID)))
                : Result.success(input);
    }

    static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case INVALID -> "Já existe um projeto com este nome.";
            default -> "Valor inválido.";
        };
    }

    static Map<String, String> values(Project project) {
        return Map.of(NAME.name(), project.name(), CLIENT.name(), project.client());
    }

    static Map<String, String> values(Form form) {
        return Map.of(NAME.name(), form.value(NAME.name()), CLIENT.name(), form.value(CLIENT.name()));
    }
}
