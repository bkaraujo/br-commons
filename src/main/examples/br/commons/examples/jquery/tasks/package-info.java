/**
 * Example app: a task list rendered entirely in the browser with jQuery ({@code static/tasks/app.js}) over a JSON
 * API that keeps the tasks in memory. Start at {@link br.commons.examples.jquery.tasks.TasksApp}.
 *
 * <p>Packages: {@code task} — the tasks, their store ({@code HashMap}) and the title field, knowing no HTTP;
 * {@code api} — the JSON API; {@code page} — the page skeleton, without data. {@code api} and {@code page} depend
 * on {@code task} only.
 */
@NullMarked
package br.commons.examples.jquery.tasks;

import org.jspecify.annotations.NullMarked;
