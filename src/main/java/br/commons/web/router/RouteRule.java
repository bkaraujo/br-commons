package br.commons.web.router;

import br.commons.Result;
import br.commons.protocol.http.message.HTTPMethod;
import org.jspecify.annotations.NullMarked;

/**
 * Registration policy that the {@link Router} client injects in the constructor — the router itself
 * does not forbid any verb other than {@code OPTIONS}, which the server responds to on its own. A failure here
 * becomes the failure of {@code register}, and the route never comes to exist.
 */
@NullMarked
@FunctionalInterface
public interface RouteRule {
    Result<Void, String> check(HTTPMethod method, String template);
}
