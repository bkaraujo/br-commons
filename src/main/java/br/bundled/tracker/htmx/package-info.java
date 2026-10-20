/**
 * Example app: a tracker of worked hours with htmx on the library's own server, rendered on the server. Start at
 * {@link TrackerApp}, which builds the seeded stores and registers the routes. The
 * models, stores, fields and totals come from {@code tracker.shared}; {@code tracker.jquery} is the same app in the
 * browser.
 *
 * <p>Packages by feature, each depending only on the ones above it in this list (and on {@code tracker.shared}):
 * <ul>
 *   <li>{@code ui} — what every screen shares: the page around it ({@code Layout}), menu, theme switch, helpers;</li>
 *   <li>{@code project}, {@code person} — the two registers: screen and routes of each;</li>
 *   <li>{@code week} — the main screen: a week filtered by person, the entry form, the table and the totals.</li>
 * </ul>
 * In each feature: {@code *Views} renders the HTML, {@code *Routes} answers the requests.
 */
@NullMarked
package br.bundled.tracker.htmx;

import org.jspecify.annotations.NullMarked;
