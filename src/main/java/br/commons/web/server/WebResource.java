package br.commons.web.server;

import br.commons.Result;
import br.commons.web.router.Route;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Slice of routes registrable in a {@link WebServer}. Implementations only declare their own routes in
 * {@link #routes()}, as the {@link Route} factories build them: the {@link WebServer} takes the list and
 * registers it, so a resource never touches the router (nor its lifecycle).
 */
@NullMarked
@FunctionalInterface
public interface WebResource {

    /** The routes of the slice; a {@code Failure} (e.g. an invalid template) is logged by the server, not thrown. */
    List<Result<Route, String>> routes();
}
