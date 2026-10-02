package br.commons.html.form;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Server-side check of submitted values against their {@link Field}s, the same for every {@code Dialect}: HTML 4
 * has no browser check and the HTML 5 one can be bypassed. Like the browser, an empty value only fails
 * {@code required}. Takes the values by name, so a view passes {@code form::all} without this package
 * depending on {@code br.commons.web}.
 */
@NullMarked
public final class Validation {

    private Validation() {}

    /** Every field that fails, in the order given. */
    public static List<Violation> check(List<Field> fields, Function<String, List<String>> values) {
        val violations = new ArrayList<Violation>();
        fields.forEach(field -> check(field, values.apply(field.name())).ifPresent(violations::add));
        return List.copyOf(violations);
    }

    public static Optional<Violation> check(Field field, List<String> values) {
        val filled = values.stream().flatMap(value -> split(field, value)).filter(value -> !value.isEmpty()).toList();
        if (filled.isEmpty()) {
            return field.required() ? Optional.of(new Violation(field.name(), ValidationRule.REQUIRED)) : Optional.empty();
        }
        return filled.stream()
                .map(value -> value(field, value))
                .flatMap(Optional::stream)
                .findFirst()
                .map(rule -> new Violation(field.name(), rule));
    }

    /** Multiple e-mails arrive comma-separated in one value. */
    private static Stream<String> split(Field field, String value) {
        return field.type() == InputType.EMAIL && field.multiple()
                ? Arrays.stream(value.split(",")).map(String::strip)
                : Stream.of(value);
    }

    /** A value of the wrong type is {@code INVALID}; otherwise its text, then its meaning, are checked. */
    private static Optional<ValidationRule> value(Field field, String value) {
        return field.type().read(value)
                .map(read -> text(field, value).or(() -> range(field, read)))
                .getOrElse(Optional.of(ValidationRule.INVALID));
    }

    private static Optional<ValidationRule> text(Field field, String value) {
        if (field.minLength() != null && value.length() < field.minLength()) {
            return Optional.of(ValidationRule.TOO_SHORT);
        }
        if (field.maxLength() != null && value.length() > field.maxLength()) {
            return Optional.of(ValidationRule.TOO_LONG);
        }
        val regex = field.regex();
        if (regex != null && !regex.matcher(value).matches()) {
            return Optional.of(ValidationRule.PATTERN);
        }
        return Optional.empty();
    }

    private static Optional<ValidationRule> range(Field field, Comparable<?> value) {
        val lower = field.lower();
        if (lower != null && compare(value, lower) < 0) {
            return Optional.of(ValidationRule.BELOW_MIN);
        }
        val upper = field.upper();
        if (upper != null && compare(value, upper) > 0) {
            return Optional.of(ValidationRule.ABOVE_MAX);
        }
        return offStep(field, value) ? Optional.of(ValidationRule.STEP) : Optional.empty();
    }

    /** Both sides were read by the same {@link InputType}, so they are of the same class. */
    @SuppressWarnings("unchecked")
    private static int compare(Comparable<?> left, Comparable<?> right) {
        return ((Comparable<Object>) left).compareTo(right);
    }

    /** As in HTML, the step counts from {@code min} (or zero). */
    private static boolean offStep(Field field, Comparable<?> value) {
        val step = field.stepSize();
        if (step == null || !(value instanceof BigDecimal number)) {
            return false;
        }
        val base = field.lower() instanceof BigDecimal lower ? lower : BigDecimal.ZERO;
        return number.subtract(base).remainder(step).signum() != 0;
    }
}
