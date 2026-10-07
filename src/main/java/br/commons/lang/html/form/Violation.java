package br.commons.lang.html.form;

import org.jspecify.annotations.NullMarked;

/** A field that failed {@link Validation}, with the first rule it broke; the view turns the rule into a message. */
@NullMarked
public record Violation(String field, ValidationRule rule) {}
