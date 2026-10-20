/**
 * Example app: the task list rendered on the server and swapped by htmx. Start at
 * {@link TasksApp}.
 *
 * <p>{@code TaskRoutes} answers the requests, {@code TaskViews} renders the page and its fragments with the HTML 5
 * DSL, {@code TaskView} is the tab and the search of the URL. The tasks and the title field come from
 * {@code tasks.shared}; the CSS ({@code static/tasks/app.css}) is the one of the jQuery version.
 */
@NullMarked
package br.bundled.tasks.htmx;

import org.jspecify.annotations.NullMarked;
