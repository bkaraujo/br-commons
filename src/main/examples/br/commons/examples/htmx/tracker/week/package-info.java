/**
 * The main screen: a week ({@link br.commons.examples.htmx.tracker.week.Week}) filtered by person
 * ({@link br.commons.examples.htmx.tracker.week.WeekFilter}, kept in the URL), the entry form and its checks
 * ({@link br.commons.examples.htmx.tracker.week.EntryFields}), the table with inline edit and the totals per day,
 * project and person ({@link br.commons.examples.htmx.tracker.week.WeekTotals}). What it reads is in
 * {@link br.commons.examples.htmx.tracker.week.WeekState}. Depends on every other package of the app.
 */
@NullMarked
package br.commons.examples.htmx.tracker.week;

import org.jspecify.annotations.NullMarked;
