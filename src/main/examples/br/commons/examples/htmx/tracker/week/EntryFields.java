package br.commons.examples.htmx.tracker.week;

import br.commons.Result;
import br.commons.examples.htmx.tracker.entry.Entry;
import br.commons.examples.htmx.tracker.entry.EntryInput;
import br.commons.examples.htmx.tracker.registry.Registered;
import br.commons.examples.htmx.tracker.registry.Registry;
import br.commons.examples.htmx.tracker.ui.Ui;
import br.commons.html.form.Field;
import br.commons.html.form.FieldSpec;
import br.commons.html.form.InputType;
import br.commons.html.form.Validation;
import br.commons.html.form.ValidationRule;
import br.commons.html.form.Violation;
import br.commons.web.server.Form;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fields of an entry, declared once: the views render them (HTML 5 types and constraints, selects of project
 * and person) and {@link #read} checks the submission on the server, plus what a field alone cannot express: the
 * project and the person exist and are active, and the end comes after the start.
 */
@NullMarked
final class EntryFields {

    static final Field DATE = FieldSpec.of("date", InputType.DATE).asRequired().build().getOrThrow();
    static final Field PROJECT = FieldSpec.of("project", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field PERSON = FieldSpec.of("person", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field DESCRIPTION = FieldSpec.of("description", InputType.TEXT).withMaxLength(120).build().getOrThrow();
    static final Field START = FieldSpec.of("start", InputType.TIME).asRequired().build().getOrThrow();
    static final Field END = FieldSpec.of("end", InputType.TIME).asRequired().build().getOrThrow();

    static final List<Field> ALL = List.of(DATE, PROJECT, PERSON, DESCRIPTION, START, END);

    private EntryFields() {}

    /**
     * The checked values, or every violation found. {@code current} is the entry being edited: it may keep a
     * project or person deactivated after it was saved.
     */
    static Result<EntryInput, List<Violation>> read(Form form, WeekState state, @Nullable Entry current) {
        val fields = Validation.check(ALL, form::all);
        if (!fields.isEmpty()) {
            return new Result.Failure<>(fields);
        }
        val violations = new ArrayList<Violation>();
        val project = Ui.number(form.value(PROJECT.name()));
        if (project == null || !usable(state.projects(), project, current == null ? null : current.projectId())) {
            violations.add(new Violation(PROJECT.name(), ValidationRule.INVALID));
        }
        val person = Ui.number(form.value(PERSON.name()));
        if (person == null || !usable(state.people(), person, current == null ? null : current.personId())) {
            violations.add(new Violation(PERSON.name(), ValidationRule.INVALID));
        }
        val start = LocalTime.parse(form.value(START.name()));
        val end = LocalTime.parse(form.value(END.name()));
        if (!end.isAfter(start)) {
            violations.add(new Violation(END.name(), ValidationRule.BELOW_MIN));
        }
        if (!violations.isEmpty() || project == null || person == null) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new EntryInput(LocalDate.parse(form.value(DATE.name())), project, person,
                form.value(DESCRIPTION.name()).strip(), start, end));
    }

    /** Exists and is active, or is what the entry already had. */
    private static boolean usable(Registry<? extends Registered> registry, int id, @Nullable Integer kept) {
        return registry.get(id).map(item -> item.active() || kept != null && kept == id).orElse(false);
    }

    static String message(Violation violation) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case BELOW_MIN -> "O fim deve ser depois do início.";
            case INVALID -> PROJECT.name().equals(violation.field()) ? "Escolha um projeto ativo."
                    : PERSON.name().equals(violation.field()) ? "Escolha uma pessoa ativa." : "Valor inválido.";
            default -> "Valor inválido.";
        };
    }

    /** The values of an entry, by field name, as the form shows them. */
    static Map<String, String> values(Entry entry) {
        return Map.of(DATE.name(), entry.date().toString(), PROJECT.name(), String.valueOf(entry.projectId()),
                PERSON.name(), String.valueOf(entry.personId()), DESCRIPTION.name(), entry.description(),
                START.name(), entry.start().toString(), END.name(), entry.end().toString());
    }

    /** The submitted values, by field name, to show them again with the errors. */
    static Map<String, String> values(Form form) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), form.value(field.name())));
        return values;
    }

    /** An empty form, dated, with the person of the filter already chosen. */
    static Map<String, String> blank(LocalDate date, WeekFilter filter) {
        return Map.of(DATE.name(), date.toString(), PERSON.name(), filter.personToken());
    }
}
