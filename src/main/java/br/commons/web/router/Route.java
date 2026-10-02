package br.commons.web.router;

import br.commons.http.MediaType;
import br.commons.http.message.HTTPMethod;
import br.commons.web.RouteHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A registered route — the source of the OpenAPI document ({@code spec.md} §4.6).
 *
 * <p>{@code produces} is mandatory in every registration: the format of the body that the route returns.
 * {@code consumes} is the format of the body that the route reads — mandatory in a verb with body and
 * {@code null} in {@code GET}/{@code DELETE}. It is by it — never by the {@code Content-Type} that the
 * client sent — that the {@code WebRequest} reads the body.
 *
 * <p>{@code requiresAuthentication} is {@code true} by default — the exception is the handful of routes
 * that need to exist without prior session (login, registration): {@code AuthenticationFilter} checks this
 * flag before requiring {@code Bearer}, since a public route is still {@code /api/*}.
 */
@NullMarked
public record Route(
        HTTPMethod method,
        String template,
        Pattern pattern,
        List<String> paramNames,
        @Nullable MediaType consumes,
        MediaType produces,
        Class<?> requestType,
        Class<?> responseType,
        boolean requiresAuthentication,
        RouteHandler handler
) {
}
