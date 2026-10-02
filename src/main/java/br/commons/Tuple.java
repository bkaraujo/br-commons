package br.commons;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record Tuple(
        String name,
        String value
) {
}
