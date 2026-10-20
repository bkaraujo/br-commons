/**
 * The main screen: a week ({@link Week}) filtered by person
 * ({@link WeekFilter}, kept in the URL), the entry form and its checks
 * ({@link EntryFields}), the table with inline edit and the totals per day,
 * project and person ({@link WeekTotals}). What it reads is in
 * {@link WeekState}. Depends on every other package of the app.
 */
@NullMarked
package br.bundled.tracker.htmx.week;

import br.bundled.tracker.shared.week.EntryFields;
import br.bundled.tracker.shared.week.Week;
import br.bundled.tracker.shared.week.WeekState;
import br.bundled.tracker.shared.week.WeekTotals;
import org.jspecify.annotations.NullMarked;
