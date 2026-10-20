package br.bundled.tracker.shared.week;


import br.commons.Result;
import br.bundled.tracker.shared.Ids;
import br.bundled.tracker.shared.entry.Entry;
import br.bundled.tracker.shared.entry.EntryInput;
import br.bundled.tracker.shared.registry.Registered;
import br.bundled.tracker.shared.registry.Registry;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The fields of an entry, declared once: the views render them (HTML 5 types and constraints, selects of project
 * and person) and {@link #read} checks the submission on the server, plus what a field alone cannot express: the
 * project and the person exist and are active, and the end comes after the start.
 */
@NullMarked
public final class EntryFields {

    public static final Field DATE = FieldSpec.of("date", InputType.DATE).asRequired().build().getOrThrow();
    public static final Field PROJECT = FieldSpec.of("project", InputType.TEXT).asRequired().build().getOrThrow();
    public static final Field PERSON = FieldSpec.of("person", InputType.TEXT).asRequired().build().getOrThrow();
    public static final Field DESCRIPTION = FieldSpec.of("description", InputType.TEXT).withMaxLength(120).build().getOrThrow();
    public static final Field START = FieldSpec.of("start", InputType.TIME).asRequired().build().getOrThrow();
    public static final Field END = FieldSpec.of("end", InputType.TIME).asRequired().build().getOrThrow();

    public static final List<Field> ALL = List.of(DATE, PROJECT, PERSON, DESCRIPTION, START, END);

    private EntryFields() {}

    /**
     * The checked values, or every violation found. {@code current} is the entry being edited: it may keep a
     * project or person deactivated after it was saved.
     */
    public static Result<EntryInput, List<Violation>> read(Function<String, String> value, WeekState state, @Nullable Entry current) {
        val fields = Validation.check(ALL, name -> List.of(value.apply(name)));
        if (!fields.isEmpty()) {
            return new Result.Failure<>(fields);
        }
        val violations = new ArrayList<Violation>();
        val project = chosen(PROJECT, value, state.projects(), current, Entry::projectId, violations);
        val person = chosen(PERSON, value, state.people(), current, Entry::personId, violations);
        val start = LocalTime.parse(value.apply(START.name()));
        val end = LocalTime.parse(value.apply(END.name()));
        if (!end.isAfter(start)) {
            violations.add(new Violation(END.name(), ValidationRule.BELOW_MIN));
        }
        if (!violations.isEmpty() || project == null || person == null) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new EntryInput(LocalDate.parse(value.apply(DATE.name())), project, person,
                value.apply(DESCRIPTION.name()).strip(), start, end));
    }

    /** The id chosen in {@code field}, or {@code null} with a violation when it is not {@link #usable}. */
    private static @Nullable Integer chosen(Field field, Function<String, String> value,
                                            Registry<? extends Registered> registry, @Nullable Entry current,
                                            ToIntFunction<Entry> kept, List<Violation> violations) {
        val id = Ids.number(value.apply(field.name()));
        if (id != null && usable(registry, id, current == null ? null : kept.applyAsInt(current))) return id;
        violations.add(new Violation(field.name(), ValidationRule.INVALID));
        return null;
    }

    /** Exists and is active, or is what the entry already had. */
    private static boolean usable(Registry<? extends Registered> registry, int id, @Nullable Integer kept) {
        return registry.get(id).map(item -> item.active() || kept != null && kept == id).orElse(false);
    }

    public static String message(Violation violation) {
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
    public static Map<String, String> values(Entry entry) {
        return Map.of(DATE.name(), entry.date().toString(), PROJECT.name(), String.valueOf(entry.projectId()),
                PERSON.name(), String.valueOf(entry.personId()), DESCRIPTION.name(), entry.description(),
                START.name(), entry.start().toString(), END.name(), entry.end().toString());
    }

    /** The submitted values, by field name, to show them again with the errors. */
    public static Map<String, String> values(Function<String, String> value) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), value.apply(field.name())));
        return values;
    }

    /** An empty form, dated, with the person of the filter ({@code ""} for everybody) already chosen. */
    public static Map<String, String> blank(LocalDate date, String person) {
        return Map.of(DATE.name(), date.toString(), PERSON.name(), person);
    }
}
