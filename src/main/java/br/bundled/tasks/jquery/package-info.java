/**
 * Example app: a task list rendered entirely in the browser with jQuery ({@code static/tasks/jquery/app.js}) over a
 * JSON API that keeps the tasks in memory. Start at {@link TasksApp}.
 *
 * <p>Packages: {@code api} — the JSON API; {@code page} — the page skeleton, without data. Both depend only on
 * {@code tasks.shared} (the tasks, their store and the title field); {@code tasks.htmx} is the same app rendered on
 * the server.
 */
@NullMarked
package br.bundled.tasks.jquery;

import org.jspecify.annotations.NullMarked;
