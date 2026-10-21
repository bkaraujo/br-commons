/**
 * The web of br.money: HTMX rendered on the server, the same pattern as {@code br.bundled.tracker.htmx}. Start at
 * {@link br.bundled.money.web.WebModule}, which loads the seed and registers the routes over the use cases.
 *
 * <p>Packages, each depending only on the ones above it in this list:
 * <ul>
 *   <li>{@code book} — what the screens read and write, over the use cases of {@code business._1_application};</li>
 *   <li>{@code seed} — the data a development run starts with (GnuCash file or fictitious);</li>
 *   <li>{@code ui} — what every screen shares: the page around it ({@code Layout}), menu, theme switch, helpers;</li>
 *   <li>{@code home}, {@code account}, {@code statement}, {@code entry}, {@code report} — one per screen.</li>
 * </ul>
 * In each feature: {@code *Views} renders the HTML, {@code *Routes} answers the requests.
 */
@NullMarked
package br.bundled.money.web;

import org.jspecify.annotations.NullMarked;
