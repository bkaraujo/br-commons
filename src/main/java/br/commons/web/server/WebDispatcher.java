package br.commons.web.server;

import br.commons.Logger;
import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPRequest;
import br.commons.http.message.HTTPResponse;
import br.commons.locale.i18n.Errors;
import br.commons.web.ProblemDetail;
import br.commons.web.router.Route;
import br.commons.web.router.Router;
import lombok.val;
import org.jspecify.annotations.NullMarked;

/**
 * Owner of the request→{@link Route} matching and handler invocation. Holds the {@link Router}
 * received in the registration of {@link WebResource} and is responsible for every HTTP transaction forwarded
 * by the {@link WebServer} — which only has custody of the {@code HTTPServer} and the registration itself.
 */
@NullMarked
public final class WebDispatcher {

    private final Router router;

    WebDispatcher(Router router) {
        this.router = router;
    }

    void register(WebResource resource) {
        resource.routes(router);
    }

    HTTPResponse handle(HTTPRequest request) {
        Logger.debug("Handling %s %s", request.method(), request.path());
        val match = router.match(request.method(), request.path());
        if (match != null) {
            val route = match.route();
            return produce(route, route.handler().handle(new WebRequest(request, route, match.pathParams())));
        }

        val allow = router.allowHeader(request.path());
        if (allow == null) return notFound();
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.METHOD_NOT_ALLOWED)).header("Allow", allow);
    }

    /**
     * The registration decides: 2xx response with body (or streaming) goes out with the {@code Content-Type}
     * of the route's {@code produces}, overwriting what the handler put. Error returned by the handler keeps its own
     * {@code Content-Type} — a {@link ProblemDetail} is JSON no matter the route — and response without body
     * does not get the header.
     */
    private static HTTPResponse produce(Route route, HTTPResponse response) {
        val code = response.status().code();
        if (code < 200 || code >= 300) return response;
        if (!response.hasBody() && !response.isStreaming()) return response;
        return response.mediaType(route.produces());
    }

    public static HTTPResponse notFound() {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND, Errors.get().routeNotFound().value()));
    }
}
