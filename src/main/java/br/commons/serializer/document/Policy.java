package br.commons.serializer.document;

import br.commons.serializer.document.binding.Scalars;
import org.jspecify.annotations.NullMarked;

/** How the {@link Document} treats a scalar that does not convert — chosen by the format reader. */
@NullMarked
public enum Policy {

    /** Wire data (JSON): raw coercion via {@link Scalars}; text that is not a number propagates the exception to the caller. */
    WIRE,

    /**
     * Configuration (YAML): text arrives trimmed ({@code trim}) and text that should be a number is a catastrophic
     * configuration failure ({@code Logger.fatal}).
     */
    CONFIG
}
