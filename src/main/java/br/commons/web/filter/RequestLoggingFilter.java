package br.commons.web.filter;

import br.commons.Logger;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPHandler;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Logs {@code METHOD path -> status} at INFO. Paths starting with an ignored prefix or ending with an ignored
 * suffix are not logged (by default static files, {@code /.well-known/} and the web manifest); a path that only
 * <em>contains</em> one of them, such as {@code /api/static-report}, is logged.
 */
@NullMarked
public final class RequestLoggingFilter implements HTTPFilter {

    public static final List<String> DEFAULT_IGNORED_PREFIXES = List.of("/static/", "/.well-known/");
    public static final List<String> DEFAULT_IGNORED_SUFFIXES = List.of(".webmanifest");

    private final List<String> ignoredPrefixes;
    private final List<String> ignoredSuffixes;

    public RequestLoggingFilter() {
        this(DEFAULT_IGNORED_PREFIXES, DEFAULT_IGNORED_SUFFIXES);
    }

    public RequestLoggingFilter(List<String> ignoredPrefixes, List<String> ignoredSuffixes) {
        this.ignoredPrefixes = List.copyOf(ignoredPrefixes);
        this.ignoredSuffixes = List.copyOf(ignoredSuffixes);
    }

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        val response = next.handle(request);
        if (!ignored(request.path()))
            Logger.info("%s %s -> %d %s", request.method(), request.path(), response.status().code(), response.status().name());

        return response;
    }

    private boolean ignored(String path) {
        for (val prefix : ignoredPrefixes) if (path.startsWith(prefix)) return true;
        for (val suffix : ignoredSuffixes) if (path.endsWith(suffix)) return true;
        return false;
    }
}
