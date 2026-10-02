package br.commons.html.form;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A checked form field, made only by {@link FieldSpec#build()}: declared once, rendered by {@code Html} in the
 * {@code Dialect} of the page and checked on the server by {@link Validation}, whatever the browser did. Keeps
 * the declaration for rendering and its parsed pattern and bounds for checking.
 */
@NullMarked
public final class Field {

    private final FieldSpec spec;
    private final @Nullable Pattern regex;
    private final @Nullable Comparable<?> lower;
    private final @Nullable Comparable<?> upper;
    private final @Nullable BigDecimal stepSize;

    Field(FieldSpec spec, @Nullable Pattern regex, @Nullable Comparable<?> lower, @Nullable Comparable<?> upper,
          @Nullable BigDecimal stepSize) {
        this.spec = spec;
        this.regex = regex;
        this.lower = lower;
        this.upper = upper;
        this.stepSize = stepSize;
    }

    public FieldSpec spec() { return spec; }
    public String name() { return spec.name(); }
    public InputType type() { return spec.type(); }
    public boolean required() { return spec.required(); }
    public @Nullable Integer minLength() { return spec.minLength(); }
    public @Nullable Integer maxLength() { return spec.maxLength(); }
    public @Nullable String pattern() { return spec.pattern(); }
    public @Nullable String min() { return spec.min(); }
    public @Nullable String max() { return spec.max(); }
    public @Nullable String step() { return spec.step(); }
    public boolean multiple() { return spec.multiple(); }

    @Nullable Pattern regex() { return regex; }
    @Nullable Comparable<?> lower() { return lower; }
    @Nullable Comparable<?> upper() { return upper; }

    /** The step to check; {@code null} with no step or {@code any}. */
    @Nullable BigDecimal stepSize() { return stepSize; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Field field && field.spec.equals(spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec);
    }

    @Override
    public String toString() {
        return spec.toString();
    }
}
