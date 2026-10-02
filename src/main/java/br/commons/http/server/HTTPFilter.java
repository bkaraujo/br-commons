package br.commons.http.server;

import br.commons.http.message.HTTPRequest;
import br.commons.http.message.HTTPResponse;
import org.jspecify.annotations.NullMarked;

/** A link in the chain: decides, calls {@code next} or not, and can transform the response. */
@NullMarked
@FunctionalInterface
public interface HTTPFilter {

    HTTPResponse apply(HTTPRequest request, HTTPHandler next);

}
