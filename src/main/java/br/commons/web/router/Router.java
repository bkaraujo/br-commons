package br.commons.web.router;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.protocol.http.message.HTTPMethod;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Explicit registration of {@link Route}s built by the client ({@code method + route template → handler}), with
 * path param extraction. No classpath scanning — each slice registers its own routes in {@code routes(Router)}.
 * Verb or template restrictions are from the client, passed as {@link RouteRule} in the constructor.
 * A registration that fails returns {@link Result.Failure} — ignoring the return does not compile.
 */
@NullMarked
@CheckReturnValue
public final class Router {

    private final List<RouteRule> rules;
    private final List<Route> routes = new ArrayList<>();

    /**
     * Routes by verb, in registration order, for {@link #match}: a request scans only its own verb, and a route
     * without path parameters is compared by {@code equals} instead of a regex. Built at registration time
     * (boot); after {@link #freeze()} it is read-only.
     */
    private final Map<HTTPMethod, Entry[]> byMethod = new EnumMap<>(HTTPMethod.class);

    @NullMarked
    private record Entry(Route route, @Nullable String literal) {}

    /** Set by {@link #freeze()} when the server starts: from then on the router is read-only, hence thread-safe. */
    private volatile boolean frozen = false;

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

    /** Registers a route built by the client; a failed build ({@link Route} factories) is passed through. */
    public Result<Route, String> register(Result<Route, String> built) {
        return built.flatMap(this::register);
    }

    public Result<Route, String> register(Route route) {
        val method = route.method();
        val template = route.template();
        if (frozen) {
            return Result.failure(Errors.get().routerFrozen(method, template).value());
        }
        if (method == HTTPMethod.OPTIONS) {
            return Result.failure(Errors.get().optionsNotRegistrable(template).value());
        }
        for (val rule : rules) {
            if (rule.check(method, template) instanceof Result.Failure<Void, String>(String error)) {
                return Result.failure(error);
            }
        }
        routes.add(route);
        val entry = new Entry(route, route.paramNames().isEmpty() ? literal(template) : null);
        byMethod.merge(method, new Entry[]{entry}, (slot, added) -> {
            val grown = Arrays.copyOf(slot, slot.length + 1);
            grown[slot.length] = added[0];
            return grown;
        });

        val current = allowed.get(template);
        val methods = current == null ? EnumSet.of(HTTPMethod.OPTIONS) : EnumSet.copyOf(current.methods());
        methods.add(method);
        allowed.put(template, new AllowedMethods(route.pattern(), methods, allowHeader(methods)));
        return Result.success(route);
    }

    /**
     * Value of {@code Allow} for {@code path} — always includes {@code OPTIONS} — or {@code null} if
     * no route matches. Templates of the same form with different parameter names
     * ({@code /x/{id}} and {@code /x/{key}}) add up.
     */
    public @Nullable String allowHeader(String path) {
        AllowedMethods found = null;
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

    public @Nullable Match match(HTTPMethod method, String path) {
        val entries = byMethod.get(method);
        if (entries == null) return null;
        for (val entry : entries) {
            val route = entry.route();
            val literal = entry.literal();
            if (literal != null) {
                if (literal.equals(path)) return new Match(route, Map.of());
                continue;
            }
            val matcher = route.pattern().matcher(path);
            if (!matcher.matches()) continue;
            return new Match(route, params(route.paramNames(), matcher));
        }
        return null;
    }

    /** Each parameter is the only capture group of its segment, so group {@code i + 1} is the {@code i}-th name. */
    private static Map<String, String> params(List<String> names, Matcher matcher) {
        if (names.size() == 1) return Map.of(names.getFirst(), matcher.group(1));

        val params = LinkedHashMap.<String, String>newLinkedHashMap(names.size());
        for (var i = 0; i < names.size(); i++) {
            params.put(names.get(i), matcher.group(i + 1));
        }

        return params;
    }

    /** The path a template without parameters matches: empty segments dropped, as {@code Route} compiles it. */
    private static String literal(String template) {
        val path = new StringBuilder(template.length());
        var start = 0;
        while (start < template.length()) {
            var end = template.indexOf('/', start);
            if (end < 0) end = template.length();
            if (end > start) path.append('/').append(template, start, end);
            start = end + 1;
        }
        return path.isEmpty() ? "/" : path.toString();
    }

    /** Ends registration. Called by {@code WebServer.start}; reads are unsynchronized, so writes must stop first. */
    public void freeze() {
        frozen = true;
    }

    public boolean frozen() {
        return frozen;
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }
}
