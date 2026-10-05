package br.commons.message;

import org.jspecify.annotations.NullMarked;

/**
 * Marker for anything publishable through {@link MessageBus}. Carries no state: a message that
 * needs its creation time declares it as a component, e.g. {@code record X(long at) implements Message}.
 */
@NullMarked
public interface Message {
}
