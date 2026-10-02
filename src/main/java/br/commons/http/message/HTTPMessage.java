package br.commons.http.message;

import br.commons.Strings;
import br.commons.http.MediaType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What request and response have in common on the wire: headers and body in bytes. Header name is
 * stored in lowercase — HTTP does not distinguish case — and {@link #header(String)} queries it this way.
 */
@NullMarked
public interface HTTPMessage {

    String CONTENT_TYPE = "Content-Type";

    /** Correlation header that the client can send and that the response returns. */
    String REQUEST_ID_HEADER = "X-REQUEST-ID";

    /** Name of the request id off the wire — key in the MDC, prefix of all request logs. */
    String REQUEST_ID = "requestId";

    /**
     * Id of the request this message belongs to: the client's {@value #REQUEST_ID_HEADER}, or one generated
     * by the server when it is missing or blank. Fixed at construction — every read returns the same value.
     */
    String requestId();

    Map<String, String> headers();

    default @Nullable String header(String name) { return headers().get(Strings.lower(name)); }

    /**
     * Format of the body according to the message's {@code Content-Type} — {@code null} if the header is missing
     * or names a type outside of {@link MediaType}. The header is the sole source: there is no separate field
     * that could diverge from it.
     */
    default @Nullable MediaType mediaType() { return MediaType.fromContentType(header(CONTENT_TYPE)); }

    byte[] body();

    default boolean hasBody() { return body().length > 0; }

}
