package br.commons.examples.tasks.jquery.api;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/** Body of a 422: {@code {"errors": [{"field": "title", "message": "…"}]}}. */
@NullMarked
record Rejection(List<FieldError> errors) {}
