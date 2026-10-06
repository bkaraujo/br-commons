package br.commons.http.server.message;

import br.commons.Result;
import br.commons.Strings;
import br.commons.http.Cookie;
import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.HTTPServer;
import br.commons.locale.i18n.Errors;
import br.commons.logger.MDC;
import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * The response that the {@link HTTPServer} knows how to write: fixed body in bytes or streaming writer.
 * Header names are lowercased in {@link #header(String, String)}. The {@link #requestId()} is the one the
 * {@link HTTPServer} published in the MDC for the request in progress — empty outside of a request.
 */
@NullMarked
public final class HTTPServerResponse implements HTTPResponse {

    private static final byte[] EMPTY = new byte[0];

    private final String requestId = Objects.requireNonNullElse(MDC.get(REQUEST_ID), Strings.EMPTY);
    private final HTTPStatus status;
    private final byte[] body;
    private final @Nullable Consumer<OutputStream> writer;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final List<Cookie> cookies = new ArrayList<>();

    private HTTPServerResponse(HTTPStatus status, byte[] body, @Nullable Consumer<OutputStream> writer) {
        this.status = status;
        this.body = body;
        this.writer = writer;
    }

    public static HTTPServerResponse status(HTTPStatus status) {
        return new HTTPServerResponse(status, EMPTY, null);
    }

    public static HTTPServerResponse status(HTTPStatus status, byte[] body) {
        return new HTTPServerResponse(status, body, null);
    }

    public static HTTPServerResponse noContent() {
        return status(HTTPStatus.NO_CONTENT);
    }

    /**
     * Long-lived connection: {@code writer} receives the raw {@link OutputStream} of the response and
     * BLOCKS the thread that calls it until the connection closes — the server runs each request in a
     * virtual thread, so this is cheap. The body is sent in chunked transfer encoding.
     */
    public static HTTPServerResponse stream(Consumer<OutputStream> writer) {
        return new HTTPServerResponse(HTTPStatus.OK, EMPTY, writer);
    }

    /** CR or LF in a name or value would split the response (header injection): refused. */
    @Override
    public HTTPServerResponse header(String name, String value) {
        if (hasLineBreak(name) || hasLineBreak(value)) {
            throw new IllegalArgumentException(Errors.get().invalidHeader(name.strip()).value());
        }
        headers.put(Strings.lower(name), value);
        return this;
    }

    private static boolean hasLineBreak(String text) {
        return text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    }

    @Override
    public HTTPServerResponse cookie(Cookie cookie) {
        cookies.add(cookie);
        return this;
    }

    @Override
    public List<Cookie> cookies() {
        return List.copyOf(cookies);
    }

    @Override
    public boolean isStreaming() {
        return writer != null;
    }

    @Override
    @CheckReturnValue
    public Result<Consumer<OutputStream>, String> streamWriter() {
        if (writer == null) return Result.failure(Errors.get().notStreaming().value());
        return Result.success(writer);
    }

    @Override
    public String requestId() { return requestId; }

    @Override
    public HTTPStatus status() { return status; }

    @Override
    public Map<String, String> headers() { return Collections.unmodifiableMap(headers); }

    @Override
    public byte[] body() { return body; }
}
