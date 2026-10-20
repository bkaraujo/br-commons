package br.bundled.money.web.ui;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.MockAccount;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Option;
import br.commons.lang.html.form.ValidationRule;
import br.commons.lang.html.form.Violation;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;

/** What the forms share: the messages of the violations, an account chosen in a select and a labeled control. */
@NullMarked
public final class Forms {

    private static final String INVALID_VALUE = "Valor inválido.";

    /** The message of a date in the closed period (INTENT F9). */
    public static final String CLOSED = "Período fechado: escolha uma data a partir da data-limite.";

    private Forms() {}

    /** A date before the closing date is a violation of {@code field}: the period is immutable. */
    public static void open(Field field, LocalDate date, Book book, List<Violation> violations) {
        if (book.closed(date)) violations.add(new Violation(field.name(), ValidationRule.INVALID));
    }

    /** The message of a violation; an invalid field says what is expected by {@code invalid} (field name → text). */
    public static String violation(Violation violation, Map<String, String> invalid) {
        return switch (violation.rule()) {
            case REQUIRED -> "Obrigatório.";
            case TOO_LONG -> "Texto longo demais.";
            case BELOW_MIN -> "Informe um valor maior que zero.";
            case STEP -> "Use no máximo 2 casas decimais.";
            case INVALID -> invalid.getOrDefault(violation.field(), INVALID_VALUE);
            default -> INVALID_VALUE;
        };
    }

    /** The id chosen in {@code field} when it is an account {@code usable}; otherwise a violation and {@code null}. */
    public static @Nullable Integer account(Field field, Function<String, String> value, Book book,
                                            Predicate<MockAccount> usable, List<Violation> violations) {
        val id = Ui.number(value.apply(field.name()));
        if (id != null && book.account(id).filter(usable).isPresent()) return id;
        violations.add(new Violation(field.name(), ValidationRule.INVALID));
        return null;
    }

    /**
     * A labeled control ({@code .field.field-<name>}), its id {@code prefix-field}: a select when there are
     * {@code options}, an input otherwise; the error of the field below it.
     */
    public static Node labeled(String prefix, Field field, String label, Map<String, String> values, List<Violation> errors,
                               @Nullable List<Option> options, Function<Violation, String> messages, Content... extra) {
        val fieldId = prefix + "-" + field.name();
        val attributes = new ArrayList<Content>(List.of(extra));
        attributes.add(id(fieldId));
        val current = values.getOrDefault(field.name(), "");
        return div(
                cls("field field-" + field.name()),
                label(htmlFor(fieldId), text(message(label))),
                options == null
                        ? input(DIALECT, field, current, attributes.toArray(Content[]::new))
                        : select(DIALECT, field, options, Set.of(current), attributes.toArray(Content[]::new)),
                Ui.errorOf(field, errors, messages)
        );
    }
}
