package br.bundled.tasks.shared;

import br.commons.lang.html.form.*;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The title of a task, declared once: the page renders it with its HTML 5 constraints (the browser checks before
 * the submit) and the API checks it again, since a request does not have to come from the page.
 */
@NullMarked
public final class TaskFields {

    public static final Field TITLE = FieldSpec.of("title", InputType.TEXT).asRequired().withMinLength(3)
            .withMaxLength(80).build().getOrThrow();

    private TaskFields() {}

    public static List<Violation> check(String title) {
        return Validation.check(List.of(TITLE), _ -> List.of(title));
    }

    /** What to tell the person; a real app takes it from a bundle. */
    public static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Informe o título.";
            case TOO_SHORT -> "Use ao menos 3 caracteres.";
            case TOO_LONG -> "Use no máximo 80 caracteres.";
            default -> "Título inválido.";
        };
    }
}
