/**
 * Example app: a tracker of worked hours with htmx on the library's own server. Start at
 * {@link br.commons.examples.htmx.tracker.TrackerApp}, which builds the stores, seeds them and registers the routes.
 *
 * <p>Packages by feature, each depending only on the ones above it in this list:
 * <ul>
 *   <li>{@code ui} — what every screen shares: the page around it ({@code Layout}), menu, theme switch, helpers;</li>
 *   <li>{@code registry} — a generic register in memory, with active and inactive items;</li>
 *   <li>{@code entry} — the entries of worked time and their store; knows no screen;</li>
 *   <li>{@code project}, {@code person} — the two registers: model, fields, screen and routes of each;</li>
 *   <li>{@code week} — the main screen: a week filtered by person, the entry form, the table and the totals.</li>
 * </ul>
 * In each feature: {@code *Fields} declares and checks the form, {@code *Views} renders the HTML, {@code *Routes}
 * answers the requests.
 */
@NullMarked
package br.commons.examples.htmx.tracker;

import org.jspecify.annotations.NullMarked;
