package br.commons.web;

import br.commons.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import org.jspecify.annotations.NullMarked;

/** What a route of {@link Router} executes — receives the request already with path params. */
@NullMarked
@FunctionalInterface
public interface RouteHandler {

    HTTPResponse handle(WebRequest request);

}
