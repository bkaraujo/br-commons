package br.commons.web.server;

import br.commons.web.router.Router;
import org.jspecify.annotations.NullMarked;

/**
 * Slice of routes registrable in a {@link WebServer}. Implementations declare their own
 * set of routes in {@link #routes(Router)}.
 */
@NullMarked
@FunctionalInterface
public interface WebResource {

    void routes(Router router);
}
