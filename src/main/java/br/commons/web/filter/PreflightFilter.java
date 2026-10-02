package br.commons.web.filter;

import br.commons.http.message.HTTPMethod;
import br.commons.http.message.HTTPRequest;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.HTTPFilter;
import br.commons.http.server.HTTPHandler;
import br.commons.web.router.Router;
import br.commons.web.server.WebDispatcher;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

/**
 * {@code OPTIONS} responds before client filters: preflight does not carry {@code Bearer} and
 * should not take 401 or 425 from catch-up. No handler runs — the {@code Allow} comes ready from the
 * cache that the {@link Router} built during registration.
 */
@NullMarked
public final class PreflightFilter implements HTTPFilter {

    private final Router router;

    public PreflightFilter(Router router) {
        this.router = router;
    }

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        if (request.method() != HTTPMethod.OPTIONS) return next.handle(request);

        val allow = router.allowHeader(request.path());
        if (allow == null) return WebDispatcher.notFound();
        return WebResponse.noContent().header("Allow", allow);
    }
}
