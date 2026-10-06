package br.commons.locale.i18n;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * Text shown to a person. Interface text only comes from a bundle ({@link Bundles}); data (a category name,
 * a bank description) enters through {@link #data(String)}. Views accept only {@code Text} where text is
 * visible, which separates, by type, interface text from data (spec §2.5, §13.2).
 */
@NullMarked
public final class Text {

    private final String value;
    private final boolean data;

    private Text(String value, boolean data) {
        this.value = value;
        this.data = data;
    }

    /** A value that belongs to the household or to the bank, shown as is. */
    public static Text data(String value) {
        return new Text(value, true);
    }

    public static Text message(String value) {
        return new Text(value, false);
    }

    public String value() {
        return value;
    }

    public boolean isData() {
        return data;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Text text && text.data == data && text.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, data);
    }

    @Override
    public String toString() {
        return value;
    }
}
