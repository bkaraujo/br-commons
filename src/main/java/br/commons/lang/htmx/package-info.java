/**
 * Support for htmx (https://htmx.org/reference/): the attributes as a static DSL beside the HTML one
 * ({@link Htmx}), the swap styles and modifiers ({@link HxSwap},
 * {@link SwapSpec}), and the {@code HX-*} headers of request ({@link HtmxRequest})
 * and response ({@link HtmxResponse}). Depends on {@code br.commons.lang.html} and on the pure HTTP
 * layer; neither depends on it ({@code HtmlArchitectureTest}).
 */
@NullMarked
package br.commons.lang.htmx;

import br.commons.lang.Htmx;
import org.jspecify.annotations.NullMarked;
