package br.commons.web.server;

import br.commons.Result;
import br.commons.http.server.HTTPFilter;
import br.commons.http.server.HTTPServer;
import br.commons.http.server.MultipartParser;
import br.commons.http.HTTPStatus;
import br.commons.web.ErrorPage;
import br.commons.web.filter.ErrorPages;
import br.commons.web.filter.PreflightFilter;
import br.commons.web.router.Router;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Custody of the {@link HTTPServer} and unique registration point: whoever sets up the process
 * registers each {@link WebResource} with {@link #register(WebResource)}. Matching routes, applying
 * {@code produces} and responding 404/405/OPTIONS is the responsibility of the internal {@link WebDispatcher}.
 */
@NullMarked
public final class WebServer {

    private final Router router;
    private final WebDispatcher dispatcher;
    private final ErrorPages pages = new ErrorPages();
    private final HTTPServer server;

    public WebServer(int port) {
        this(port, List.of());
    }

    public WebServer(int port, List<HTTPFilter> filters) {
        this(port, new Router(), filters, HTTPServer.defaultMaxBodySize());
    }

    public WebServer(int port, Router router, List<HTTPFilter> filters) {
        this(port, router, filters, HTTPServer.defaultMaxBodySize());
    }

    public WebServer(int port, List<HTTPFilter> filters, long maxBodySize) {
        this(port, new Router(), filters, maxBodySize);
    }

    /** {@code maxBodySize}: requests with a larger body get 413 before any filter (spec §14.1). */
    public WebServer(int port, Router router, List<HTTPFilter> filters, long maxBodySize) {
        this(port, router, filters, maxBodySize, null);
    }

    /** {@code multipartLimits}: uploads streamed to temporary files instead of memory (spec §5.2). */
    public WebServer(int port, List<HTTPFilter> filters, long maxBodySize, MultipartParser.@Nullable Limits multipartLimits) {
        this(port, new Router(), filters, maxBodySize, multipartLimits);
    }

    public WebServer(int port, Router router, List<HTTPFilter> filters, long maxBodySize,
                     MultipartParser.@Nullable Limits multipartLimits) {
        this.router = router;
        dispatcher = new WebDispatcher(router);
        val chain = new ArrayList<HTTPFilter>(filters.size() + 2);
        chain.add(pages);
        chain.add(new PreflightFilter(router));
        chain.addAll(filters);
        server = new HTTPServer(port, chain, dispatcher::handle, failure -> WebResponse.internalServerError(), maxBodySize, multipartLimits);
    }

    public void register(WebResource resource) {
        dispatcher.register(resource);
    }

    /**
     * HTML page for {@code status} (any status), served instead of the error body when the client accepts
     * {@code text/html}; API clients keep the {@code ProblemDetail}. Covers filters (e.g. {@code StaticFiles}) too.
     */
    @CheckReturnValue
    public Result<Void, String> errorPage(HTTPStatus status, ErrorPage page) {
        return pages.register(status, page);
    }

    /** {@link #errorPage(HTTPStatus, ErrorPage)} with a static HTML read from the classpath. */
    @CheckReturnValue
    public Result<Void, String> errorPage(HTTPStatus status, String classpathResource) {
        return pages.register(status, classpathResource);
    }

    /** Built-in pages for 404 and 500; register yours afterwards to replace one of them. */
    @CheckReturnValue
    public Result<Void, String> defaultErrorPages() {
        return pages.defaults();
    }

    /** Freezes the {@link Router} first: a registration after the start returns {@code Failure}. */
    @CheckReturnValue
    public Result<Void, String> start() {
        router.freeze();
        pages.freeze();
        return server.start();
    }

    public void stop() {
        server.stop();
    }

    /** Stops accepting connections and waits up to {@code grace} for the requests in progress. */
    public void stop(Duration grace) {
        server.stop(grace);
    }

    public int port() {
        return server.port();
    }
}
