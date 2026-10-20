/**
 * What both versions of the worked hours tracker share ({@code htmx} renders on the server, {@code jquery} in the
 * browser over a JSON API): the models, the stores, the fields and their checks, the week and its totals. Knows no
 * HTTP and no HTML; the {@code *Fields} read values by field name, from a form or from a JSON body.
 *
 * <p>Packages: {@code registry} (generic register), {@code entry} (entries of worked time), {@code project} and
 * {@code person} (the two registers), {@code week} (the week, the entry fields, the totals) and {@link Seed}.
 */
@NullMarked
package br.bundled.tracker.shared;

import org.jspecify.annotations.NullMarked;
