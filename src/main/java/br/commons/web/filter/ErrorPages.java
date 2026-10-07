package br.commons.web.filter;

import br.commons.Logger;
import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.locale.l10n.L10N;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPMessage;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.web.ErrorPage;
import br.commons.web.WebResponse;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link ErrorPage} by status (any status) and the filter that applies it. A response whose status
 * has a page is replaced by the page only when the client accepts {@code text/html} (browser navigation) and the
 * response is not already HTML; API clients keep the {@code ProblemDetail}. Status, headers (e.g. {@code Allow})
 * and cookies of the original response are kept. Exception in the chain with a page for 500 → the page.
 *
 * <p>Empty by default; {@link #defaults()} registers the built-in 404 and 500 pages. Registration is closed by
 * {@link #freeze()} (called by {@code WebServer.start()}); afterwards the map is read-only, safe between threads.
 */
@NullMarked
public final class ErrorPages implements HTTPFilter {

    private final Map<Integer, ErrorPage> pages = new HashMap<>();
    private volatile boolean frozen;

    /** Registers (or replaces) the page of {@code status}. */
    @CheckReturnValue
    public Result<Void, String> register(HTTPStatus status, ErrorPage page) {
        if (frozen) return Result.failure(Errors.get().errorPagesFrozen().value());
        pages.put(status.code(), page);
        return Result.success();
    }

    /** Registers a static HTML from the classpath, read once now; missing resource → {@code Failure}. */
    @CheckReturnValue
    public Result<Void, String> register(HTTPStatus status, String classpathResource) {
        val name = classpathResource.startsWith("/") ? classpathResource.substring(1) : classpathResource;
        try (InputStream in = ErrorPages.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return Result.failure(Errors.get().errorPageNotFound(classpathResource).value());
            val html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return register(status, (request, s) -> html);
        } catch (IOException e) {
            return Result.failure(Errors.get().errorPageReadFailed(classpathResource, String.valueOf(e.getMessage())).value());
        }
    }

    /** Built-in pages for 404 and 500. */
    @CheckReturnValue
    public Result<Void, String> defaults() {
        return register(HTTPStatus.NOT_FOUND, ErrorPages::builtin)
                .flatMap(ignored -> register(HTTPStatus.INTERNAL_SERVER_ERROR, ErrorPages::builtin));
    }

    public void freeze() {
        frozen = true;
    }

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        if (pages.isEmpty() || !acceptsHtml(request)) return next.handle(request);
        HTTPResponse response;
        try {
            response = next.handle(request);
        } catch (RuntimeException | Error e) {
            val page = fatal(request, e);
            if (page == null) throw e;
            return page;
        }
        return replace(request, response);
    }

    /** The 500 page for an exception in the chain; {@code null} without one (the caller rethrows). */
    private @Nullable HTTPResponse fatal(HTTPRequest request, Throwable error) {
        val page = pages.get(HTTPStatus.INTERNAL_SERVER_ERROR.code());
        if (page == null) return null;
        Logger.error("Fatal error dispatching %s: %s", request.path(), error.toString());
        return render(page, request, HTTPStatus.INTERNAL_SERVER_ERROR, null);
    }

    private HTTPResponse replace(HTTPRequest request, HTTPResponse response) {
        if (response.isStreaming() || response.mediaType() == MediaType.TEXT_HTML) return response;
        val page = pages.get(response.status().code());
        if (page == null) return response;
        return render(page, request, response.status(), response);
    }

    private static HTTPResponse render(ErrorPage errorPage, HTTPRequest request, HTTPStatus status, @Nullable HTTPResponse original) {
        val page = WebResponse.html(status, errorPage.html(request, status));
        if (original == null) return page;
        original.headers().forEach((name, value) -> {
            if (!name.equalsIgnoreCase(HTTPMessage.CONTENT_TYPE) && !name.equalsIgnoreCase("Content-Length")) page.header(name, value);
        });
        original.cookies().forEach(page::cookie);
        return page;
    }

    /** Browser navigation lists {@code text/html}; {@code fetch}/HTMX send {@code *}{@code /*} and keep JSON. */
    static boolean acceptsHtml(HTTPRequest request) {
        val accept = request.header("Accept");
        return accept != null && accept.toLowerCase().contains("text/html");
    }

    private static String builtin(HTTPRequest request, HTTPStatus status) {
        return """
                <!doctype html>
                <html lang="%4$s">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$d %2$s</title>
                <style>body{font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;\
                background:#f6f6f6;color:#222}main{text-align:center;padding:16px}h1{font-size:4rem;margin:0}\
                a{color:#0b5cad}</style></head>
                <body><main><h1>%1$d</h1><p>%3$s</p><p><a href="/">%5$s</a></p></main></body>
                </html>
                """.formatted(status.code(), escape(status.name()), escape(status.description()),
                L10N.locale().toLanguageTag(), escape(Errors.get().errorPageBackHome().value()));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
