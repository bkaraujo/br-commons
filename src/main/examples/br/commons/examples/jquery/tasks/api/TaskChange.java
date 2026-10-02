package br.commons.examples.jquery.tasks.api;

import org.jspecify.annotations.NullMarked;

/** Body of {@code PATCH /api/tasks/{id}}: {@code {"done": true}}. */
@NullMarked
record TaskChange(boolean done) {}
