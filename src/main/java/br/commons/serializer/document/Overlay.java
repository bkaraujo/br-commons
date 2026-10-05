package br.commons.serializer.document;

import br.commons.Logger;
import br.commons.serializer.Serializer;
import br.commons.serializer.document.binding.Scalars;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * {@link Serializer} with an override layer by key: all scalar reads consult {@code overrides}
 * first and only fall back to {@code base} when it has no value for the key. The source is set by the caller —
 * system properties, a test map — and is consulted on each call, so a value set after creation takes effect.
 * Structure ({@code list}, {@code subtree}) and binding delegate to {@code base}, without override; scalar
 * lists as text ({@code asInts}…) come from {@link Serializer}'s defaults applied to this class's {@link #asString}.
 *
 * <p>Present override never silently falls back to the document value. Text that should be a number is a
 * catastrophic configuration failure ({@link Logger#fatal}, which terminates the process). Invalid enum or UUID returns
 * {@code null} (and the default, in variants with default).
 */
@NullMarked
public final class Overlay implements Serializer {

    private final Serializer base;
    private final Function<String, @Nullable String> overrides;

    public Overlay(Serializer base, Function<String, @Nullable String> overrides) {
        this.base = base;
        this.overrides = overrides;
    }

    Serializer base() {
        return base;
    }

    private <T> @Nullable T pick(String key, Class<T> type, Function<String, @Nullable T> fallback) {
        val raw = overrides.apply(key);
        if (raw == null) return fallback.apply(key);

        try {
            return Scalars.coerce(raw.trim(), type);
        } catch (NumberFormatException e) {
            Logger.fatal("Expected a number in override of %s", key);
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override @Nullable public String  asString  (String key) { return pick(key, String.class, base::asString); }
    @Override @Nullable public Byte    asByte    (String key) { return pick(key, Byte.class, base::asByte); }
    @Override @Nullable public Short   asShort   (String key) { return pick(key, Short.class, base::asShort); }
    @Override @Nullable public Integer asInt     (String key) { return pick(key, Integer.class, base::asInt); }
    @Override @Nullable public Long    asLong    (String key) { return pick(key, Long.class, base::asLong); }
    @Override @Nullable public Float   asFloat   (String key) { return pick(key, Float.class, base::asFloat); }
    @Override @Nullable public Double  asDouble  (String key) { return pick(key, Double.class, base::asDouble); }
    @Override @Nullable public Boolean asBoolean (String key) { return pick(key, Boolean.class, base::asBoolean); }

    @Override public boolean contains(String key) { return overrides.apply(key) != null || base.contains(key); }
    @Override public boolean isEmpty() { return base.isEmpty(); }

    /** The base document for the type: overrides apply only to individual values, not to binding. */
    @Override public <T> T as(Class<T> type) { return base.as(type); }
    @Override public <T> List<T> asList(Class<T> type) { return base.asList(type); }
    @Override @Nullable public Serializer subtree(String key) { return base.subtree(key); }
    @Override public List<Serializer> list(String key) { return base.list(key); }
    @Override @Nullable public Path folder() { return base.folder(); }

    @Override
    public String toString() {
        return base.toString();
    }
}
