package br.commons.html.form;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Declaration of a form field, still unchecked: {@link #build()} checks it and gives the {@link Field} that
 * {@code Html} and {@link Validation} take, or the reason the declaration is inconsistent.
 *
 * <pre>{@code
 * FieldSpec.of("cpf", InputType.TEXT).asRequired().withMaxLength(14).withPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}").build()
 * }</pre>
 *
 * @param pattern Java regular expression matched against the whole value, like the HTML {@code pattern}
 * @param min     lower bound in the type's value syntax ({@code 2026-01-01}, {@code 0.5}); ordered types only
 * @param step    {@code any} or a positive number; {@link InputType#NUMBER} and {@link InputType#RANGE} only
 */
@NullMarked
public record FieldSpec(String name, InputType type, boolean required, @Nullable Integer minLength,
                        @Nullable Integer maxLength, @Nullable String pattern, @Nullable String min,
                        @Nullable String max, @Nullable String step, boolean multiple) {

    private static final Set<InputType> STEPPED = EnumSet.of(InputType.NUMBER, InputType.RANGE);

    public static FieldSpec of(String name, InputType type) {
        return new FieldSpec(name, type, false, null, null, null, null, null, null, false);
    }

    public FieldSpec asRequired() {
        return new FieldSpec(name, type, true, minLength, maxLength, pattern, min, max, step, multiple);
    }

    public FieldSpec withMinLength(int length) {
        return new FieldSpec(name, type, required, length, maxLength, pattern, min, max, step, multiple);
    }

    public FieldSpec withMaxLength(int length) {
        return new FieldSpec(name, type, required, minLength, length, pattern, min, max, step, multiple);
    }

    public FieldSpec withPattern(String regex) {
        return new FieldSpec(name, type, required, minLength, maxLength, regex, min, max, step, multiple);
    }

    public FieldSpec withMin(String value) {
        return new FieldSpec(name, type, required, minLength, maxLength, pattern, value, max, step, multiple);
    }

    public FieldSpec withMax(String value) {
        return new FieldSpec(name, type, required, minLength, maxLength, pattern, min, value, step, multiple);
    }

    public FieldSpec withStep(String value) {
        return new FieldSpec(name, type, required, minLength, maxLength, pattern, min, max, value, multiple);
    }

    /** Several values: a multiple {@code <select>}, comma-separated e-mails, several files. */
    public FieldSpec asMultiple() {
        return new FieldSpec(name, type, required, minLength, maxLength, pattern, min, max, step, true);
    }

    /** The checked field, with pattern and bounds already read; or why the declaration is inconsistent. */
    public Result<Field, String> build() {
        return lengths()
                .flatMap(ignored -> stepSize())
                .flatMap(size -> regex()
                        .flatMap(regex -> bound(min)
                                .flatMap(lower -> bound(max)
                                        .map(upper -> new Field(this, regex, lower, upper, size)))));
    }

    private Result<Void, String> lengths() {
        if (name.isBlank()) {
            return Result.failure(Errors.get().fieldWithoutName().value());
        }
        if (negative(minLength) || negative(maxLength)) {
            return Result.failure(Errors.get().negativeLength(name).value());
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            return Result.failure(Errors.get().minLengthAboveMax(String.valueOf(minLength), String.valueOf(maxLength), name).value());
        }
        return Result.success();
    }

    private static boolean negative(@Nullable Integer length) {
        return length != null && length < 0;
    }

    private Result<@Nullable Pattern, String> regex() {
        return pattern == null ? Result.success(null) : Attempt.parse(pattern, Pattern::compile);
    }

    private Result<@Nullable Comparable<?>, String> bound(@Nullable String value) {
        if (value == null) {
            return Result.success(null);
        }
        if (!type.ordered()) {
            return Result.failure(Errors.get().boundOnUnordered(value, type.token(), name).value());
        }
        return type.read(value);
    }

    /** {@code null} for no step or {@code any}. */
    private Result<@Nullable BigDecimal, String> stepSize() {
        if (step == null) {
            return Result.success(null);
        }
        if (!STEPPED.contains(type)) {
            return Result.failure(Errors.get().stepNotApplicable(type.token(), name).value());
        }
        if ("any".equals(step)) {
            return Result.success(null);
        }
        return Attempt.parse(step, BigDecimal::new)
                .flatMap(size -> size.signum() > 0
                        ? Result.success(size)
                        : Result.failure(Errors.get().stepNotPositive(step).value()));
    }
}
