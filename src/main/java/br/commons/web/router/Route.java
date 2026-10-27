package br.commons.web.router;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.web.RouteHandler;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A route — the source of the OpenAPI document ({@code spec.md} §4.6). Clients build it with the factories
 * ({@link #get}, {@link #post}...) and hand it to {@link Router#register(Result)}.
 *
 * <p>{@code produces} is mandatory in every route: the format of the body that the route returns.
 * {@code consumes} is the format of the body that the route reads — mandatory in a verb with body and
 * {@code null} in {@code GET}/{@code DELETE}. It is by it — never by the {@code Content-Type} that the
 * client sent — that the {@code WebRequest} reads the body.
 *
 * <p>A factory fails ({@link Result.Failure}) when the template has an invalid or repeated path parameter.
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
        boolean authenticated,
        RouteHandler handler
) {

    public static Result<Route, String> get(String template, MediaType produces, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        return of(HTTPMethod.GET, template, null, produces, Void.class, responseType, authenticated, handler);
    }

    public static Result<Route, String> post(String template, MediaType consumes, MediaType produces, Class<?> requestType, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        return of(HTTPMethod.POST, template, consumes, produces, requestType, responseType, authenticated, handler);
    }

    public static Result<Route, String> put(String template, MediaType consumes, MediaType produces, Class<?> requestType, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        return of(HTTPMethod.PUT, template, consumes, produces, requestType, responseType, authenticated, handler);
    }

    public static Result<Route, String> patch(String template, MediaType consumes, MediaType produces, Class<?> requestType, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        return of(HTTPMethod.PATCH, template, consumes, produces, requestType, responseType, authenticated, handler);
    }

    public static Result<Route, String> delete(String template, MediaType produces, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        return of(HTTPMethod.DELETE, template, null, produces, Void.class, responseType, authenticated, handler);
    }

    /** Any verb; {@code consumes} {@code null} makes a route without body. */
    public static Result<Route, String> of(HTTPMethod method, String template, @Nullable MediaType consumes, MediaType produces, Class<?> requestType, Class<?> responseType, boolean authenticated, RouteHandler handler) {
        val paramNames = new ArrayList<String>();
        return compile(template, paramNames).map(pattern -> new Route(method, template, pattern, List.copyOf(paramNames), consumes, produces, requestType, responseType, authenticated, handler));
    }

    /**
     * Template → regex, once at registration; the pattern it builds is what {@code Router.match} runs on every request,
     * so it is built to match fast: a parameter is {@code [^/]++} (possessive — it is always followed by {@code /} or
     * the end, so there is nothing to backtrack into) and the template is scanned in one pass, without {@code split}.
     */
    private static Result<Pattern, String> compile(String template, List<String> paramNames) {
        val length = template.length();
        val regex = new StringBuilder(length * 2 + 8).append('^');
        var start = 0;
        while (start < length) {
            var end = template.indexOf('/', start);
            if (end < 0) end = length;
            if (end > start) {
                val error = segment(regex.append('/'), template, start, end, paramNames);
                if (error != null) return Result.failure(error);
            }
            start = end + 1;
        }
        if (regex.length() == 1) regex.append('/');
        return Result.success(Pattern.compile(regex.append('$').toString()));
    }

    /** Appends the regex of the segment {@code template[start, end)}; the error message when its parameter is invalid. */
    private static @Nullable String segment(StringBuilder regex, String template, int start, int end, List<String> paramNames) {
        if (template.charAt(start) != '{' || template.charAt(end - 1) != '}') {
            regex.append(Pattern.quote(template.substring(start, end)));
            return null;
        }
        val name = template.substring(start + 1, end - 1);
        val error = param(name, template, paramNames);
        if (error == null) regex.append("(?<").append(name).append(">[^/]++)");
        return error;
    }

    /** Registers a path parameter name; the error message when it is invalid or repeated. */
    private static @Nullable String param(String name, String template, List<String> paramNames) {
        if (!identifier(name)) return Errors.get().invalidPathParameter(name, template).value();
        if (paramNames.contains(name)) return Errors.get().repeatedPathParameter(name, template).value();
        paramNames.add(name);
        return null;
    }

    /** Path parameter names become regex group names: ASCII letters and digits, starting with a letter. */
    private static boolean identifier(String name) {
        if (name.isEmpty() || !letter(name.charAt(0))) return false;
        for (var i = 1; i < name.length(); i++) {
            val c = name.charAt(i);
            if (!letter(c) && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    private static boolean letter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

}
