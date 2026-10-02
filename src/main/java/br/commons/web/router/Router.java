package br.commons.web.router;

import br.commons.Result;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPMethod;
import br.commons.web.RouteHandler;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Explicit registration {@code method + route template → handler}, with path param extraction.
 * No classpath scanning — each slice registers its own routes in {@code routes(Router)}.
 * Every registration declares the {@link MediaType} it produces; a verb with body also declares what it consumes
 * ({@code GET} and {@code DELETE} have no body) — nothing is assumed.
 * Verb or template restrictions are from the client, passed as {@link RouteRule} in the constructor.
 * A registration that fails returns {@link Result.Failure} — ignoring the return does not compile.
 */
@NullMarked
@CheckReturnValue
public final class Router {

    private final List<RouteRule> rules;
    private final List<Route> routes = new ArrayList<>();

    public Router(RouteRule... rules) {
        this.rules = List.of(rules);
    }

    /**
     * Cache of methods by template, built at registration time: {@code OPTIONS} and {@code 405} read the
     * {@code Allow} ready instead of scanning {@link #routes} by method for each request. Written only
     * at boot (before {@code HTTPServer.start}); after that it is concurrent reading.
     */
    private final Map<String, AllowedMethods> allowed = new LinkedHashMap<>();

    @NullMarked
    private record AllowedMethods(Pattern pattern, EnumSet<HTTPMethod> methods, String header) {}

    public Result<Route, String> get(String template, MediaType produces, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.GET, template, null, produces, Void.class, responseType, true, handler);
    }

    public Result<Route, String> post(String template, MediaType consumes, MediaType produces,
                                      Class<?> requestType, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.POST, template, consumes, produces, requestType, responseType, true, handler);
    }

    public Result<Route, String> put(String template, MediaType consumes, MediaType produces,
                                     Class<?> requestType, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.PUT, template, consumes, produces, requestType, responseType, true, handler);
    }

    public Result<Route, String> patch(String template, MediaType consumes, MediaType produces,
                                       Class<?> requestType, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.PATCH, template, consumes, produces, requestType, responseType, true, handler);
    }

    public Result<Route, String> delete(String template, MediaType produces, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.DELETE, template, null, produces, Void.class, responseType, true, handler);
    }

    /** Route {@code /api/*} reachable without {@code Bearer} — login, registration. */
    public Result<Route, String> postPublic(String template, MediaType consumes, MediaType produces,
                                            Class<?> requestType, Class<?> responseType, RouteHandler handler) {
        return register(HTTPMethod.POST, template, consumes, produces, requestType, responseType, false, handler);
    }

    /** {@code consumes} {@code null} registers a route without body. */
    public Result<Route, String> register(HTTPMethod method, String template, @Nullable MediaType consumes, MediaType produces,
                                          Class<?> requestType, Class<?> responseType, RouteHandler handler) {
        return register(method, template, consumes, produces, requestType, responseType, true, handler);
    }

    public Result<Route, String> register(HTTPMethod method, String template, @Nullable MediaType consumes, MediaType produces,
                                          Class<?> requestType, Class<?> responseType,
                                          boolean requiresAuthentication, RouteHandler handler) {
        if (method == HTTPMethod.OPTIONS) {
            return Result.failure("OPTIONS is not registered (" + template + ") — the server responds based on the routes");
        }
        for (val rule : rules) {
            if (rule.check(method, template) instanceof Result.Failure<Void, String>(String error)) {
                return Result.failure(error);
            }
        }
        val paramNames = new ArrayList<String>();
        val pattern = compile(template, paramNames);
        val route = new Route(method, template, pattern, paramNames, consumes, produces, requestType, responseType,
                requiresAuthentication, handler);
        routes.add(route);

        val current = allowed.get(template);
        val methods = current == null ? EnumSet.of(HTTPMethod.OPTIONS) : EnumSet.copyOf(current.methods());
        methods.add(method);
        allowed.put(template, new AllowedMethods(pattern, methods, allowHeader(methods)));
        return Result.success(route);
    }

    /**
     * Value of {@code Allow} for {@code path} — always includes {@code OPTIONS} — or {@code null} if
     * no route matches. Templates of the same form with different parameter names
     * ({@code /x/{id}} and {@code /x/{key}}) add up.
     */
    public @Nullable String allowHeader(String path) {
        @Nullable AllowedMethods found = null;
        for (val entry : allowed.values()) {
            if (!entry.pattern().matcher(path).matches()) continue;
            if (found == null) {
                found = entry;
                continue;
            }
            val union = EnumSet.copyOf(found.methods());
            union.addAll(entry.methods());
            found = new AllowedMethods(found.pattern(), union, allowHeader(union));
        }
        return found == null ? null : found.header();
    }

    private static String allowHeader(EnumSet<HTTPMethod> methods) {
        return methods.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static Pattern compile(String template, List<String> paramNames) {
        val regex = new StringBuilder("^");
        for (val segment : template.split("/")) {
            if (segment.isEmpty()) continue;
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                val name = segment.substring(1, segment.length() - 1);
                paramNames.add(name);
                regex.append("(?<").append(name).append(">[^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        if (regex.length() == 1) regex.append('/');
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    @NullMarked
    public record Match(Route route, Map<String, String> pathParams) {}

    public @Nullable Match match(HTTPMethod method, String path) {
        for (val route : routes) {
            if (route.method() != method) continue;
            val matcher = route.pattern().matcher(path);
            if (!matcher.matches()) continue;

            val params = new LinkedHashMap<String, String>();
            for (val name : route.paramNames()) {
                params.put(name, matcher.group(name));
            }
            return new Match(route, params);
        }
        return null;
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }
}
