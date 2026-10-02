package br.commons.examples.jquery.tasks.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /api/tasks}: {@code {"title": "…"}}; the title may be missing in a bad request. */
@NullMarked
record NewTask(@Nullable String title) {}
