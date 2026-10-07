/**
 * Pure HTTP layer: the message model on the wire ({@link HTTPMessage},
 * {@link HTTPRequest}, {@link HTTPResponse},
 * {@link HTTPMethod}, {@link HTTPStatus}) and, in
 * {@link br.commons.protocol.http.server}, the server with the filter chain. Headers and body in bytes —
 * routing, JSON and error format are conveniences of {@code br.commons.web}, which depends on this and
 * never the other way around ({@code HTTPArchitectureTest}).
 */
@NullMarked
package br.commons.protocol.http;

import br.commons.protocol.http.message.HTTPMessage;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import org.jspecify.annotations.NullMarked;
