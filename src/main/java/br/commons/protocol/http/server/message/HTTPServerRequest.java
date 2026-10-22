package br.commons.protocol.http.server.message;

import br.commons.Result;
import br.commons.Strings;
import br.commons.locale.i18n.Errors;
import br.commons.protocol.http.Multipart;
import br.commons.protocol.http.message.HTTPMessage;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.server.HTTPServer;
import br.commons.protocol.http.server.MultipartParser;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The request as the {@link HTTPServer} reads it from the exchange. Header names are lowercased in
 * construction (the first one wins if only the case differs); id, parameters and headers stay immutable, only
 * attributes change along the chain.
 */
@NullMarked
public final class HTTPServerRequest implements HTTPRequest {

    private final String requestId;
    private final HTTPMethod method;
    private final URI uri;
    private final String path;
    private final Map<String, String> parameters;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Multipart multipart;
    private final Map<String, Object> attributes = new HashMap<>();

    /** A request built off the wire (tests, internal dispatch) from {@code uri} as text, e.g. {@code /x?since=10}. */
    public HTTPServerRequest(String requestId, HTTPMethod method, String uri, Map<String, String> headers, byte[] body) {
        this(requestId, method, URI.create(uri), headers, body, Multipart.EMPTY);
    }

    /**
     * Reads {@link #path()} and {@link #parameters()} from {@code uri}, the raw target the client sent
     * ({@code /people/?q=caf%C3%A9}): the path decoded and without trailing slash, the query decoded. A
     * {@code multipart/form-data} body was streamed by the {@link MultipartParser}, not kept in memory.
     *
     * @throws IllegalArgumentException when the query is malformed (see {@link #parseQuery(String)}, which the
     *                                  server checks first to answer 400)
     */
    public HTTPServerRequest(String requestId, HTTPMethod method, URI uri, Map<String, String> headers, byte[] body,
                             Multipart multipart) {
        this(requestId, method, uri, query(uri.getRawQuery()), headers, body, multipart);
    }

    /** {@link #HTTPServerRequest(String, HTTPMethod, URI, Map, byte[], Multipart)} with the query already decoded. */
    public HTTPServerRequest(String requestId, HTTPMethod method, URI uri, Map<String, String> parameters,
                             Map<String, String> headers, byte[] body, Multipart multipart) {
        this.multipart = multipart;
        this.requestId = requestId;
        this.method = method;
        this.uri = uri;
        this.path = withoutTrailingSlash(Strings.or(uri.getPath(), ""));
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.headers = Collections.unmodifiableMap(lowerCaseNames(headers));
        this.body = body;
    }

    private static Map<String, String> query(@Nullable String rawQuery) {
        val query = parseQuery(rawQuery);
        if (query instanceof Result.Failure<Map<String, String>, String>(var error)) throw new IllegalArgumentException(error);
        return query.getOrThrow();
    }

    private static String withoutTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * The query string decoded, or why it cannot be: malformed percent-encoding ({@code %zz}, a lone {@code %})
     * fails, so the server answers 400 instead of failing the request.
     */
    public static Result<Map<String, String>, String> parseQuery(@Nullable String rawQuery) {
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

    private static Map<String, String> lowerCaseNames(Map<String, String> headers) {
        val normalized = new LinkedHashMap<String, String>();
        headers.forEach((name, value) -> normalized.putIfAbsent(Strings.lower(name), value));
        return normalized;
    }

    @Override
    public String requestId() { return requestId; }

    /**
     * The client's {@value HTTPMessage#REQUEST_ID_HEADER} when valid ({@code [A-Za-z0-9._-]{1,64}}), empty
     * otherwise. Correlation only: per-request state is keyed by {@link #requestId()}, which the server generates.
     */
    public String clientRequestId() {
        return clientRequestId(header(HTTPMessage.REQUEST_ID_HEADER));
    }

    /** What a client may send as {@value HTTPMessage#REQUEST_ID_HEADER}: no spaces, CR/LF or log markup. */
    private static final Pattern CLIENT_REQUEST_ID_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** {@code header} when it matches {@code [A-Za-z0-9._-]{1,64}}; empty otherwise (never logged raw). */
    public static String clientRequestId(@Nullable String header) {
        return header != null && CLIENT_REQUEST_ID_FORMAT.matcher(header).matches() ? header : "";
    }

    @Override
    public HTTPMethod method() { return method; }

    @Override
    public URI uri() { return uri; }

    @Override
    public String path() { return path; }

    @Override
    public Map<String, String> parameters() { return parameters; }

    @Override
    public Map<String, String> headers() { return headers; }

    @Override
    public byte[] body() { return body; }

    @Override
    public Multipart multipart() { return multipart; }

    @Override
    public Map<String, Object> attributes() { return attributes; }
}
