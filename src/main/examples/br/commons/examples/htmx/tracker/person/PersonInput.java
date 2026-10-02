package br.commons.examples.htmx.tracker.person;

import org.jspecify.annotations.NullMarked;

/** The values of a person read from a form and already checked by {@link PersonFields}. */
@NullMarked
public record PersonInput(String name, String email) {}
