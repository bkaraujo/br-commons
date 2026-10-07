package br.commons.protocol.http.server;

import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import org.jspecify.annotations.NullMarked;

/** Transforms the request into a response — the end of the {@link HTTPFilter} chain and the {@code next} of each link. */
@NullMarked
@FunctionalInterface
public interface HTTPHandler {

    HTTPResponse handle(HTTPRequest request);

}
