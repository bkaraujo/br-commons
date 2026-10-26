package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Option;
import br.commons.lang.html.form.ValidationRule;
import br.commons.lang.html.form.Violation;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;

/** What the forms share: the messages of the violations, an account chosen in a select and a labeled control. */
@NullMarked
public final class Forms {

    private static final String INVALID_VALUE = "forms.common.invalid";

    /** The message of a date in the closed period (INTENT F9). */
    public static final String CLOSED = "forms.common.period_closed";

    private Forms() {}

    /** A date before the closing date is a violation of {@code field}: the period is immutable. */
    public static void open(Field field, LocalDate date, Book book, List<Violation> violations) {
        if (book.closed(date)) violations.add(new Violation(field.name(), ValidationRule.INVALID));
    }

    /**
     * A value typed in reais, as {@code money.js} shows it ({@code 1.234,56}) or as typed without JavaScript
     * ({@code 1234,5}, {@code 1234.50}): with a comma, the dots group thousands; without one, a dot is the decimal
     * point. Two decimal places at most; {@code null} for anything else.
     */
    public static @Nullable BigDecimal money(String text) {
        val plain = text.replace("R$", "").strip();
        val decimal = plain.contains(",") ? plain.replace(".", "").replace(',', '.') : plain;
        return MONEY.matcher(decimal).matches() ? new BigDecimal(decimal).setScale(2, RoundingMode.UNNECESSARY) : null;
    }

    private static final Pattern MONEY = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?");

    /** The message of a violation; an invalid field says what is expected by {@code invalid} (field name → text). */
    public static String violation(Violation violation, Map<String, String> invalid) {
        return switch (violation.rule()) {
            case REQUIRED -> "forms.common.required";
            case TOO_LONG -> "forms.common.too_long";
            case BELOW_MIN -> "forms.common.below_min";
            case STEP -> "forms.common.step";
            case INVALID -> invalid.getOrDefault(violation.field(), INVALID_VALUE);
            default -> INVALID_VALUE;
        };
    }

    /** The account chosen in {@code field} when it is {@code usable}; otherwise a violation and {@code null}. */
    public static @Nullable Account account(Field field, Function<String, String> value, Book book,
                                            Predicate<Account> usable, List<Violation> violations) {
        val found = book.account(Ui.uuid(value.apply(field.name()))).filter(usable);
        if (found.isPresent()) return found.get();
        violations.add(new Violation(field.name(), ValidationRule.INVALID));
        return null;
    }

    /**
     * A labeled control ({@code .field.field-<name>}), its id {@code prefix-field}: a select when there are
     * {@code options}, an input otherwise; the error of the field below it.
     */
    public static Node labeled(String prefix, Field field, String label, Map<String, String> values, List<Violation> errors,
                               @Nullable List<Option> options, Function<Violation, String> messages, Content... extra) {
        val current = values.getOrDefault(field.name(), "");
        val attributes = attributes(prefix, field, extra);
        return labeled(prefix, field, label, errors, messages, options == null
                ? input(DIALECT, field, current, attributes)
                : select(DIALECT, field, options, Set.of(current), attributes));
    }

    /** {@link #labeled} for free text over several lines: a {@code textarea}. */
    public static Node labeledText(String prefix, Field field, String label, Map<String, String> values,
                                   List<Violation> errors, Function<Violation, String> messages, Content... extra) {
        return labeled(prefix, field, label, errors, messages,
                textarea(DIALECT, field, values.getOrDefault(field.name(), ""), attributes(prefix, field, extra)));
    }

    private static Content[] attributes(String prefix, Field field, Content... extra) {
        val attributes = new ArrayList<>(List.of(extra));
        attributes.add(id(prefix + "-" + field.name()));
        return attributes.toArray(Content[]::new);
    }

    private static Node labeled(String prefix, Field field, String label, List<Violation> errors,
                                Function<Violation, String> messages, Node control) {
        return div(
                cls("field field-" + field.name()),
                label(htmlFor(prefix + "-" + field.name()), text(message(label))),
                control,
                Ui.errorOf(field, errors, messages)
        );
    }
}
