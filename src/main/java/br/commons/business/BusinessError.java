package br.commons.business;

import br.commons.Logger;
import br.commons.locale.i18n.I18N;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Domain error. {@code key} is a translation key (see {@link I18N}), not ready text —
 * {@link #render(Locale)} is the only point that becomes text, resolved in the locale of whoever asks (the
 * HTTP request, typically), never at the instant the error is born inside the use case.
 *
 * <p>Records compare {@code args} by content ({@link Arrays#equals}), not by array reference.
 */
@NullMarked
public sealed interface BusinessError {

    String key();
    Object[] args();

    default String render(Locale locale) { return I18N.of(key(), locale, args()); }

    default String render() { return I18N.of(key(), args()); }

    default void toConsole() { Logger.error(render()); }

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

    /**
     * A failure outside the domain (port taken, worker that does not stop), whose text the library already localized
     * ({@code Errors}): there is no key to translate, so {@link #render} gives it as is. {@link #key()} is the text.
     */
    @NullMarked
    record Infrastructure(String message) implements BusinessError {
        @Override public String key() { return message; }
        @Override public Object[] args() { return new Object[0]; }
        @Override public String render(Locale locale) { return message; }
        @Override public String render() { return message; }
    }
}
