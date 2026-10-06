/**
 * The main screen: a week ({@link br.commons.examples.tracker.shared.week.Week}) filtered by person
 * ({@link br.commons.examples.tracker.htmx.week.WeekFilter}, kept in the URL), the entry form and its checks
 * ({@link br.commons.examples.tracker.shared.week.EntryFields}), the table with inline edit and the totals per day,
 * project and person ({@link br.commons.examples.tracker.shared.week.WeekTotals}). What it reads is in
 * {@link br.commons.examples.tracker.shared.week.WeekState}. Depends on every other package of the app.
 */
@NullMarked
package br.commons.examples.tracker.htmx.week;

import org.jspecify.annotations.NullMarked;
