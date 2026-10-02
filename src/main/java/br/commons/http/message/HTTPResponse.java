package br.commons.http.message;

import br.commons.Result;
import br.commons.http.Cookie;
import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.NullMarked;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Raw HTTP response: status, headers and body in bytes — or, on a long-lived connection, whoever
 * writes to the {@link OutputStream}. Does not serialize anything: whoever builds it decides the format and
 * declares it in {@link #mediaType(MediaType)}.
 */
@NullMarked
public interface HTTPResponse extends HTTPMessage {

    HTTPStatus status();

    /** Adds (or replaces) a header and returns the response itself, for chaining. */
    @CanIgnoreReturnValue
    HTTPResponse header(String name, String value);

    /** Declares the body format: writes {@code Content-Type} with {@link MediaType#contentType()}. */
    @CanIgnoreReturnValue
    default HTTPResponse mediaType(MediaType mediaType) { return header(CONTENT_TYPE, mediaType.contentType()); }

    boolean isStreaming();

    /** Who writes the body of a streaming response; fails if the response has a fixed body. */
    @CheckReturnValue
    Result<Consumer<OutputStream>, String> streamWriter();


    /** Adds a {@code Set-Cookie}; unlike {@link #header}, multiple ones coexist. */
    @CanIgnoreReturnValue
    HTTPResponse cookie(Cookie cookie);

    List<Cookie> cookies();
}
