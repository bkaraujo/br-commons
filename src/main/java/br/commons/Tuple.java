package br.commons;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Tuple(
        String name,
        String value
) implements Cloneable {

    @Override
    public Tuple clone() {
        return new Tuple(name, value);
    }
}
