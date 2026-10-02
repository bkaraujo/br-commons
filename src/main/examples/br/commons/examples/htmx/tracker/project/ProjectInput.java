package br.commons.examples.htmx.tracker.project;

import org.jspecify.annotations.NullMarked;

/** The values of a project read from a form and already checked by {@link ProjectFields}. */
@NullMarked
public record ProjectInput(String name, String client) {}
