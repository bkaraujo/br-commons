/**
 * Server over {@code com.sun.net.httpserver} ({@link HTTPServer}): reads the
 * raw exchange in {@link HTTPServerRequest}, passes through the
 * {@link HTTPFilter} chain to the {@link HTTPHandler} and writes
 * the response — {@link HTTPServerResponse} is the concrete implementation, with
 * factories for fixed and streaming body.
 */
@NullMarked
package br.commons.http.server;

import br.commons.http.server.message.HTTPServerRequest;
import br.commons.http.server.message.HTTPServerResponse;
import org.jspecify.annotations.NullMarked;
