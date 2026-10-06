/**
 * What both versions of the task list share ({@code htmx} renders on the server, {@code jquery} in the browser over
 * a JSON API): the tasks, their store in memory and the title field (declared once, rendered by the page and checked
 * by the server). Knows no HTTP.
 */
@NullMarked
package br.commons.examples.tasks.shared;

import org.jspecify.annotations.NullMarked;
