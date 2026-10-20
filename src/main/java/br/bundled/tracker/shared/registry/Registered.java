package br.bundled.tracker.shared.registry;

import org.jspecify.annotations.NullMarked;

/** What a {@link Registry} keeps: an id, a name to list and choose by, and whether it can still be chosen. */
@NullMarked
public interface Registered {

    int id();

    String name();

    boolean active();
}
