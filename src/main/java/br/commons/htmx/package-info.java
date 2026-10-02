/**
 * Support for htmx (https://htmx.org/reference/): the attributes as a static DSL beside the HTML one
 * ({@link br.commons.htmx.Htmx}), the swap styles and modifiers ({@link br.commons.htmx.HxSwap},
 * {@link br.commons.htmx.SwapSpec}), and the {@code HX-*} headers of request ({@link br.commons.htmx.HtmxRequest})
 * and response ({@link br.commons.htmx.HtmxResponse}). Depends on {@code br.commons.html} and on the pure HTTP
 * layer; neither depends on it ({@code HtmlArchitectureTest}).
 */
@NullMarked
package br.commons.htmx;

import org.jspecify.annotations.NullMarked;
