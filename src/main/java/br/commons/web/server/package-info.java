/**
 * The server ready for a {@link Router} ({@link WebServer}): dispatch to the matched route ({@link WebDispatcher}),
 * {@code OPTIONS} answered from the router ({@link PreflightFilter}) and the request with path params and JSON body
 * ({@link WebRequest}). Responses come from {@link br.commons.web.WebResponse}.
 */
@NullMarked
package br.commons.web.server;

import br.commons.web.router.Router;
import org.jspecify.annotations.NullMarked;
