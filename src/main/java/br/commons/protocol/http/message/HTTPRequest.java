package br.commons.protocol.http.message;

import br.commons.protocol.http.Multipart;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;

/**
 * Normalized HTTP request: id, method, path without trailing slash, query string parameters,
 * raw headers and body. Does not know about routes or carry session state: what a filter resolves and
 * what the route needs to read is passed through {@link #attribute(String, Object)}.
 */
@NullMarked
public interface HTTPRequest extends HTTPMessage {

    HTTPMethod method();

    String path();

    /**
     * The request target as the client sent it, as the browser shows it after the host: its {@code toString()} is
     * the raw path (no trailing slash removal, percent-encoding kept) and, when present, {@code ?} plus the raw
     * query — e.g. {@code /people/?q=caf%C3%A9&q=x}. The fragment never reaches the server. For logs and links
     * back to the same address; routing uses {@link #path()} and reading uses {@link #parameters()}.
     */
    URI uri();

    /** Query string already decoded; repeated parameter takes the last value. */
    Map<String, String> parameters();

    default @Nullable String parameter(String name) { return parameters().get(name); }

    Map<String, Object> attributes();

    default void attribute(String key, Object value) { attributes().put(key, value); }

    default @Nullable Object attribute(String key) { return attributes().get(key); }

    /** The streamed {@code multipart/form-data} body (spec §5.2); empty for any other request. */
    default Multipart multipart() { return Multipart.EMPTY; }


    /** Value of the {@code name} cookie in the {@code Cookie} header, or {@code null}. The first one wins if repeated. */
    default @Nullable String cookie(String name) {
        val header = header("Cookie");
        if (header == null) return null;
        for (val part : header.split(";")) {
            val equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).trim().equals(name)) return part.substring(equals + 1).trim();
        }
        return null;
    }
}
