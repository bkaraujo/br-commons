package br.bundled.tasks.jquery.api;

import org.jspecify.annotations.NullMarked;

/** A field the server refused, with the message to show next to it. */
@NullMarked
record FieldError(String field, String message) {}
