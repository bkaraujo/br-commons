package br.commons.business;

import br.commons.i18n.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Domain error. {@code key} is a translation key (see {@link Messages}), not ready text —
 * {@link #render(Locale)} is the only point that becomes text, resolved in the locale of whoever asks (the
 * HTTP request, typically), never at the instant the error is born inside the use case.
 *
 * <p>Records compare {@code args} by content ({@link Arrays#equals}), not by array reference.
 */
@NullMarked
public sealed interface BusinessError {

    String key();
    Object[] args();

    default String render(Locale locale) {
        return Messages.of(key(), locale, args());
    }

    @NullMarked
    record NotFound(String key, Object... args) implements BusinessError {
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof NotFound(var otherKey, var otherArgs)
                    && key.equals(otherKey) && Arrays.equals(args, otherArgs);
        }
        @Override public int hashCode() { return 31 * key.hashCode() + Arrays.hashCode(args); }
        @Override public String toString() { return "NotFound[key=" + key + ", args=" + Arrays.toString(args) + "]"; }
    }

    @NullMarked
    record BusinessRule(String key, Object... args) implements BusinessError {
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof BusinessRule(var otherKey, var otherArgs)
                    && key.equals(otherKey) && Arrays.equals(args, otherArgs);
        }
        @Override public int hashCode() { return 31 * key.hashCode() + Arrays.hashCode(args); }
        @Override public String toString() { return "BusinessRule[key=" + key + ", args=" + Arrays.toString(args) + "]"; }
    }

    @NullMarked
    record Validation(String key, Object... args) implements BusinessError {
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof Validation(var otherKey, var otherArgs)
                    && key.equals(otherKey) && Arrays.equals(args, otherArgs);
        }
        @Override public int hashCode() { return 31 * key.hashCode() + Arrays.hashCode(args); }
        @Override public String toString() { return "Validation[key=" + key + ", args=" + Arrays.toString(args) + "]"; }
    }

    @NullMarked
    record Conflict(String key, Object... args) implements BusinessError {
        @Override public boolean equals(@Nullable Object other) {
            return other instanceof Conflict(var otherKey, var otherArgs)
                    && key.equals(otherKey) && Arrays.equals(args, otherArgs);
        }
        @Override public int hashCode() { return 31 * key.hashCode() + Arrays.hashCode(args); }
        @Override public String toString() { return "Conflict[key=" + key + ", args=" + Arrays.toString(args) + "]"; }
    }

}
