/**
 * The web of br.money: HTMX rendered on the server, the same pattern as {@code br.bundled.tracker.htmx}. Start at
 * {@link br.bundled.money.web.WebModule}, which builds the book and registers the routes.
 *
 * <p>Packages, each depending only on the ones above it in this list:
 * <ul>
 *   <li>{@code mock} — the in-memory book, stand-in for the application layer;</li>
 *   <li>{@code ui} — what every screen shares: the page around it ({@code Layout}), menu, theme switch, helpers;</li>
 *   <li>{@code home}, {@code account}, {@code entry}, {@code report} — one per screen.</li>
 * </ul>
 * In each feature: {@code *Views} renders the HTML, {@code *Routes} answers the requests.
 */
@NullMarked
package br.bundled.money.web;

import org.jspecify.annotations.NullMarked;
