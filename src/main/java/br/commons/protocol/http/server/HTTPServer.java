package br.commons.protocol.http.server;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.logger.MDC;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.Multipart;
import br.commons.protocol.http.message.HTTPMessage;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import com.google.errorprone.annotations.CheckReturnValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Over {@code com.sun.net.httpserver}, without external framework ({@code plan.md} §1): one virtual
 * thread per request, the {@link HTTPFilter} chain around a {@link HTTPHandler} and the bytes of the
 * {@link HTTPResponse} on the wire. Does not know routing, JSON or error format — a failure that escapes
 * the chain becomes the {@code onFailure} response.
 */
@NullMarked
public final class HTTPServer {

    private final int port;
    private final HTTPHandler chain;
    private final Function<Throwable, HTTPResponse> onFailure;
    /** Maximum body size read into memory; above it the request receives 413 without passing through the chain (spec §14.1). */
    private final long maxBodySize;
    /** Streams {@code multipart/form-data} bodies to disk instead of memory; {@code null} reads them like any other body. */
    private final @Nullable MultipartParser multipart;
    private @Nullable HttpServer server;

    public HTTPServer(int port, List<HTTPFilter> filters, HTTPHandler handler) {
        this(port, filters, handler, failure -> HTTPServerResponse.status(HTTPStatus.INTERNAL_SERVER_ERROR));
    }

    public HTTPServer(int port, List<HTTPFilter> filters, HTTPHandler handler, Function<Throwable, HTTPResponse> onFailure) {
        this(port, filters, handler, onFailure, defaultMaxBodySize());
    }

    /** System property that overrides {@link #DEFAULT_MAX_BODY_SIZE} for the constructors without a limit. */
    public static final String MAX_BODY_SIZE_PROPERTY = "br.commons.http.maxBodySize";
    public static final long DEFAULT_MAX_BODY_SIZE = 1024 * 1024;

    /**
     * Body limit of the constructors that take none: {@value #MAX_BODY_SIZE_PROPERTY} (bytes) when set and valid,
     * else 1 MiB. Read at construction. Unlimited needs the explicit {@code Long.MAX_VALUE}.
     */
    public static long defaultMaxBodySize() {
        val configured = System.getProperty(MAX_BODY_SIZE_PROPERTY);
        if (configured == null) return DEFAULT_MAX_BODY_SIZE;
        try {
            val parsed = Long.parseLong(configured.strip());
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // falls through to the warning
        }
        Logger.warn("Invalid %s: %s — using %s bytes", MAX_BODY_SIZE_PROPERTY, configured, DEFAULT_MAX_BODY_SIZE);
        return DEFAULT_MAX_BODY_SIZE;
    }

    public HTTPServer(int port, List<HTTPFilter> filters, HTTPHandler handler, Function<Throwable, HTTPResponse> onFailure, long maxBodySize) {
        this(port, filters, handler, onFailure, maxBodySize, null);
    }

    /**
     * {@code multipartLimits}: where and how big {@code multipart/form-data} uploads may be (spec §5.2); such a body
     * is streamed to temporary files, bounded by its own request limit instead of {@code maxBodySize}.
     */
    public HTTPServer(int port, List<HTTPFilter> filters, HTTPHandler handler, Function<Throwable, HTTPResponse> onFailure,
                      long maxBodySize, MultipartParser.@Nullable Limits multipartLimits) {
        this.multipart = multipartLimits == null ? null : new MultipartParser(multipartLimits);
        this.maxBodySize = maxBodySize;
        this.port = port;
        this.chain = compose(List.copyOf(filters), handler);
        this.onFailure = onFailure;
    }

    @CheckReturnValue
    public Result<Void, String> start() {
        try {
            val httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", this::dispatch);
            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpServer.start();
            server = httpServer;
            Logger.info("HTTP server listening on port %d", port);
            return Result.success();
        } catch (IOException e) {
            return Result.failure(Errors.get().httpServerStartFailed(String.valueOf(port), e).value());
        }
    }

    public void stop() {
        stop(Duration.ZERO);
    }

    /** Stops accepting connections and waits for in-flight requests for up to {@code grace}. */
    public void stop(Duration grace) {
        // HttpServer takes whole seconds: round up so a sub-second grace is not cut to zero.
        if (server != null) server.stop((int) Math.min(Integer.MAX_VALUE, Math.ceilDiv(grace.toMillis(), 1000)));
    }

    /** The port on which the server listens — useful when it starts on port 0. */
    public int port() {
        return server == null ? port : server.getAddress().getPort();
    }

    private static HTTPHandler compose(List<? extends HTTPFilter> filters, HTTPHandler handler) {
        var composed = handler;
        for (int i = filters.size() - 1; i >= 0; i--) {
            val filter = filters.get(i);
            val name = Meta.fqn(filter);
            Logger.debug("Attaching %s", name);

            val next = composed;
            composed = request -> {
                Logger.trace("Executing %s", name);
                return filter.apply(request, next);
            };
        }
        return composed;
    }

    private void dispatch(HttpExchange exchange) {
        // Identity is always the server's; the client's id is kept apart, only to correlate logs and the response.
        val requestId = UUID.randomUUID().toString();
        val clientRequestId = HTTPServerRequest.clientRequestId(exchange.getRequestHeaders().getFirst(HTTPMessage.REQUEST_ID_HEADER));
        MDC.push(HTTPMessage.REQUEST_ID, requestId);
        if (!clientRequestId.isEmpty()) MDC.push(HTTPMessage.CLIENT_REQUEST_ID, clientRequestId);
        HTTPServerRequest request = null;

        try {

            val method = method(exchange.getRequestMethod());
            if (method == null) {
                write(exchange, HTTPServerResponse.status(HTTPStatus.METHOD_NOT_ALLOWED));
                return;
            }

            Logger.debug("[IN ] %s %s %s", exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI());
            request = readRequest(exchange, requestId, method);
            if (request != null) write(exchange, chain.handle(request));
        } catch (Throwable e) {
            // Throwable, not just RuntimeException: an Error (e.g. IncompatibleClassChangeError from old class)
            // would close the connection without response or log.
            Logger.error("Fatal error dispatching %s: %s", exchange.getRequestURI(), e.toString());
            writeSafely(exchange, onFailure.apply(e));
        } finally {
            if (request != null) deleteQuietly(request.multipart());
            exchange.close();
            MDC.pop(HTTPMessage.REQUEST_ID);
            if (!clientRequestId.isEmpty()) MDC.pop(HTTPMessage.CLIENT_REQUEST_ID);
        }
    }

    /**
     * Reads the query and the body — streamed to disk for {@code multipart/form-data}, in memory otherwise — or
     * answers 400 (malformed query or multipart) or 413 and returns {@code null} when it cannot.
     */
    private @Nullable HTTPServerRequest readRequest(HttpExchange exchange, String requestId, HTTPMethod method) throws IOException {
        val query = parseQuery(exchange.getRequestURI().getRawQuery());
        if (query instanceof Result.Failure<Map<String, String>, String>(var error)) {
            Logger.debug("[IN ] malformed query: %s", error);
            write(exchange, HTTPServerResponse.status(HTTPStatus.BAD_REQUEST));
            return null;
        }
        val parameters = query.getOrElse(Map.of());
        val contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (multipart != null && MultipartParser.accepts(contentType)) {
            try {
                return toRequest(exchange, requestId, method, parameters, new byte[0], multipart.parse(contentType, exchange.getRequestBody()));
            } catch (MultipartParser.TooLarge e) {
                Logger.trace("[IN ]CONTENT_TOO_LARGE (multipart)");
                write(exchange, HTTPServerResponse.status(HTTPStatus.CONTENT_TOO_LARGE));
            } catch (IOException | IllegalStateException e) {
                Logger.debug("[IN ] malformed multipart: %s", e.toString());
                write(exchange, HTTPServerResponse.status(HTTPStatus.BAD_REQUEST));
            }
            return null;
        }

        val body = readBody(exchange.getRequestBody(), maxBodySize);
        if (body == null) {
            Logger.trace("[IN ]CONTENT_TOO_LARGE");
            write(exchange, HTTPServerResponse.status(HTTPStatus.CONTENT_TOO_LARGE));
            return null;
        }
        return toRequest(exchange, requestId, method, parameters, body, Multipart.EMPTY);
    }

    /** The client's {@value HTTPMessage#REQUEST_ID_HEADER}, or a new one when it is missing or blank. */
    /** Method outside {@link HTTPMethod} (HEAD, TRACE…) receives 405 instead of blowing up as a failure. */
    private static @Nullable HTTPMethod method(String name) {
        for (val method : HTTPMethod.values()) {
            if (method.name().equals(name)) return method;
        }
        return null;
    }

    /**
     * The entire body, or {@code null} when it exceeds {@code limit} bytes. Reads one byte past the limit to tell
     * "exactly the limit" from "over it"; {@code limit + 1} is never computed, since it overflows for
     * {@code Long.MAX_VALUE} (no limit) and would read nothing.
     */
    static byte @Nullable [] readBody(InputStream input, long limit) throws IOException {
        return readBody(input, limit, Integer.MAX_VALUE - 8);
    }

    /**
     * {@link #readBody(InputStream, long)} with the largest array the JVM allocates ({@code cap}), a parameter only
     * so a test can lower it. A limit at or above the cap reads up to it and then checks for one more byte: a
     * larger body is too large however high the limit, never truncated.
     */
    static byte @Nullable [] readBody(InputStream input, long limit, int cap) throws IOException {
        if (limit < cap) {
            val bytes = input.readNBytes((int) limit + 1);
            return bytes.length > limit ? null : bytes;
        }
        val bytes = input.readNBytes(cap);
        return input.read() == -1 ? bytes : null;
    }

    /** The uploaded files never outlive the request (spec §5.2). */
    private static void deleteQuietly(Multipart upload) {
        try {
            upload.delete();
        } catch (UncheckedIOException e) {
            Logger.warn("Could not delete an uploaded temporary file: %s", e.toString());
        }
    }

    private static HTTPServerRequest toRequest(HttpExchange exchange, String requestId, HTTPMethod method,
                                               Map<String, String> parameters, byte[] body, Multipart upload) {
        val rawPath = exchange.getRequestURI().getPath();
        val path = rawPath.length() > 1 && rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;

        val headers = new LinkedHashMap<String, String>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name, values.getFirst());
        });

        Logger.debug("[IN ] headers %s", headers);
        Logger.debug("[IN ] parameters %s", parameters);
        Logger.verbose("[IN ] body %s", Logger.lazy(() -> Arrays.toString(body)));
        return new HTTPServerRequest(requestId, method, path, parameters, headers, body, upload);
    }

    /**
     * The query string decoded, or why it cannot be: malformed percent-encoding ({@code %zz}, a lone {@code %})
     * answers 400 instead of failing the request.
     */
    static Result<Map<String, String>, String> parseQuery(@Nullable String rawQuery) {
        val query = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isBlank()) return Result.success(query);
        for (val pair : rawQuery.split("&")) {
            val idx = pair.indexOf('=');
            if (idx < 0) continue;
            val entry = decode(pair.substring(0, idx))
                    .flatMap(key -> decode(pair.substring(idx + 1)).map(value -> Map.entry(key, value)));
            if (entry instanceof Result.Failure<Map.Entry<String, String>, String>(var error)) return Result.failure(error);
            entry.ifSuccess(decoded -> query.put(decoded.getKey(), decoded.getValue()));
        }
        return Result.success(query);
    }

    /** {@link URLDecoder} fails by exception; this is where it becomes a {@link Result}. */
    private static Result<String, String> decode(String text) {
        try {
            return Result.success(URLDecoder.decode(text, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Result.failure(Errors.get().malformedQuery(String.valueOf(e.getMessage())).value());
        }
    }

    /** Discards what the original response left in the headers; if they were already sent, nothing to do. */
    private static void writeSafely(HttpExchange exchange, HTTPResponse response) {
        try {
            exchange.getResponseHeaders().clear();
            write(exchange, response);
        } catch (IOException ignored) {
            // Connection already lost or headers already sent — nothing to do.
        }
    }

    private static void write(HttpExchange exchange, HTTPResponse response) throws IOException {
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        response.cookies().forEach(cookie -> exchange.getResponseHeaders().add("Set-Cookie", cookie.header()));
        // Echo the client's id when it sent a valid one (end-to-end correlation); otherwise the server's.
        val echoed = Objects.requireNonNullElse(MDC.get(HTTPMessage.CLIENT_REQUEST_ID), response.requestId());
        if (!echoed.isEmpty()) exchange.getResponseHeaders().set(HTTPMessage.REQUEST_ID_HEADER, echoed);
        if (response.isStreaming()) { writeStreaming(exchange, response); return; }

        val body = response.body();
        exchange.sendResponseHeaders(response.status().code(), body.length == 0 ? -1 : body.length);
        if (body.length == 0) return;

        try (val out = exchange.getResponseBody()) {
            Logger.debug("[OUT] %s %s %s :: %s bytes", exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI(), body.length);
            Logger.trace("[OUT] headers %s", response.headers());
            Logger.trace("[OUT] cookies %s", response.cookies());
            Logger.verbose("[OUT] body %s", Logger.lazy(() -> Arrays.toString(body)));
            out.write(body);
        }
    }

    /**
     * {@code responseLength} 0 enables chunked transfer encoding — the only way to send a body without
     * knowing the size beforehand, required by a connection that keeps writing indefinitely (SSE).
     * Client disconnection becomes an {@link IOException} in the middle of writing — routine, not an error: exits
     * without propagating to the generic {@code catch} of {@link #dispatch}, which would try to write the failure response
     * with headers already sent.
     */
    private static void writeStreaming(HttpExchange exchange, HTTPResponse response) {
        try {
            exchange.sendResponseHeaders(response.status().code(), 0);

            try (val out = exchange.getResponseBody()) {
                Logger.debug("[STR] %s %s %s", exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI());
                response.streamWriter().ifSuccess(writer -> writer.accept(out));
            }
        } catch (IOException e) {
            Logger.debug("Streaming connection closed: %s", e.toString());
        }
    }
}
