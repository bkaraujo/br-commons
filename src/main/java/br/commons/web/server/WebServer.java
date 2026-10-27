package br.commons.web.server;

import br.commons.Logger;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.module.Lifecycle;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPServer;
import br.commons.protocol.http.server.MultipartParser;
import br.commons.web.ErrorPage;
import br.commons.web.WebResponse;
import br.commons.web.filter.ErrorPages;
import br.commons.web.router.Router;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Custody of the {@link HTTPServer} and unique registration point: whoever sets up the process
 * registers each {@link WebResource} with {@link #register(WebResource...)}. Matching routes, applying
 * {@code produces} and responding 404/405/OPTIONS is the responsibility of the internal {@link WebDispatcher}.
 *
 * <p>As a {@link Lifecycle}, {@link #initialize()} runs the {@link HTTPServer} on a platform thread of its own
 * (not daemon), which keeps the process alive until {@link #terminate()}.
 */
@NullMarked
public final class WebServer implements Lifecycle {

    private final Router router;
    private final WebDispatcher dispatcher;
    private final ErrorPages pages = new ErrorPages();
    private final HTTPServer server;
    private final CountDownLatch stopped = new CountDownLatch(1);

    public WebServer(int port) {
        this(port, List.of());
    }

    public WebServer(int port, List<? extends HTTPFilter> filters) {
        this(port, new Router(), filters, HTTPServer.defaultMaxBodySize());
    }

    public WebServer(int port, Router router, List<? extends HTTPFilter> filters) {
        this(port, router, filters, HTTPServer.defaultMaxBodySize());
    }

    public WebServer(int port, List<? extends HTTPFilter> filters, long maxBodySize) {
        this(port, new Router(), filters, maxBodySize);
    }

    /** {@code maxBodySize}: requests with a larger body get 413 before any filter (spec §14.1). */
    public WebServer(int port, Router router, List<? extends HTTPFilter> filters, long maxBodySize) {
        this(port, router, filters, maxBodySize, null);
    }

    /** {@code multipartLimits}: uploads streamed to temporary files instead of memory (spec §5.2). */
    public WebServer(int port, List<? extends HTTPFilter> filters, long maxBodySize, MultipartParser.@Nullable Limits multipartLimits) {
        this(port, new Router(), filters, maxBodySize, multipartLimits);
    }

    public WebServer(int port, Router router, List<? extends HTTPFilter> filters, long maxBodySize, MultipartParser.@Nullable Limits multipartLimits) {
        this.router = router;
        dispatcher = new WebDispatcher(router);
        val chain = new ArrayList<HTTPFilter>(filters.size() + 2);
        chain.add(pages);
        chain.add(new PreflightFilter(router));
        chain.addAll(filters);
        server = new HTTPServer(port, chain, dispatcher::handle, _ -> WebResponse.internalServerError(), maxBodySize, multipartLimits);
    }

    /**
     * Takes the routes of each resource and hands every one that was built to the dispatcher; a route that failed to
     * build is logged at ERROR and skipped, so neither it nor a refused registration stops the server or the rest.
     */
    public void register(WebResource ... resources) {
        Arrays.stream(resources)
                .flatMap(resource -> resource.routes().stream())
                .filter(result -> result.ifFailure(Logger::error).isSuccess())
                .map(Result::get)
                .forEach(dispatcher::register);
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

    /** Starts on a new platform thread and waits for the outcome; the thread lives until {@link #terminate()}. */
    @Override
    public Result<Void, BusinessError> initialize() {
        val started = new CompletableFuture<Result<Void, String>>();
        Thread.ofPlatform().name("web-server").daemon(false).start(() -> {
            val result = start();
            started.complete(result);
            if (result.isSuccess()) awaitStop();
        });
        return started.join().mapFailure(BusinessError.Infrastructure::new);
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        stop();
        stopped.countDown();
        return Result.success();
    }

    private void awaitStop() {
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int port() {
        return server.port();
    }
}
