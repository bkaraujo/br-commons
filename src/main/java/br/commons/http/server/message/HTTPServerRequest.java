package br.commons.http.server.message;

import br.commons.Strings;
import br.commons.http.Multipart;
import br.commons.http.message.HTTPMethod;
import br.commons.http.message.HTTPRequest;
import br.commons.http.server.HTTPServer;
import br.commons.http.server.MultipartParser;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The request as the {@link HTTPServer} reads it from the exchange. Header names are lowercased in
 * construction (the first one wins if only the case differs); id, parameters and headers stay immutable, only
 * attributes change along the chain.
 */
@NullMarked
public final class HTTPServerRequest implements HTTPRequest {

    private final String requestId;
    private final HTTPMethod method;
    private final String path;
    private final Map<String, String> parameters;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Multipart multipart;
    private final Map<String, Object> attributes = new HashMap<>();

    public HTTPServerRequest(String requestId, HTTPMethod method, String path, Map<String, String> parameters,
                             Map<String, String> headers, byte[] body) {
        this(requestId, method, path, parameters, headers, body, Multipart.EMPTY);
    }

    /** A {@code multipart/form-data} request: its body was streamed by the {@link MultipartParser}, not kept in memory. */
    public HTTPServerRequest(String requestId, HTTPMethod method, String path, Map<String, String> parameters,
                             Map<String, String> headers, byte[] body, Multipart multipart) {
        this.multipart = multipart;
        this.requestId = requestId;
        this.method = method;
        this.path = path;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.headers = Collections.unmodifiableMap(lowerCaseNames(headers));
        this.body = body;
    }

    private static Map<String, String> lowerCaseNames(Map<String, String> headers) {
        val normalized = new LinkedHashMap<String, String>();
        headers.forEach((name, value) -> normalized.putIfAbsent(Strings.lower(name), value));
        return normalized;
    }

    @Override
    public String requestId() { return requestId; }

    @Override
    public HTTPMethod method() { return method; }

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
