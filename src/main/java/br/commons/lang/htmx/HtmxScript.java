package br.commons.lang.htmx;

import br.commons.lang.Htmx;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Serves the htmx library bundled beside this package ({@code br/commons/lang/htmx/<version>.min.js}) at
 * {@link #PATH}, so an application loads it from itself, not from a CDN. Add it to the filters of the server and put
 * {@link Htmx#htmxScript()} in the {@code <head>}. The path carries the version: the file never changes under it,
 * so it is cached for a year and {@code If-None-Match} answers 304.
 */
@NullMarked
public final class HtmxScript implements HTTPFilter {

    /** The version of htmx bundled. */
    public static final String VERSION = "2.0.11";

    /** Where the script is served: the same path it has on the classpath. */
    public static final String
            PATH = "/br/commons/lang/htmx/" + VERSION + ".min.js";

    private static final String ETAG = "\"htmx-" + VERSION + "\"";
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private volatile byte @Nullable [] bytes;

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        if (request.method() != HTTPMethod.GET || !request.path().equals(PATH)) return next.handle(request);
        if (ETAG.equals(request.header("If-None-Match"))) {
            return HTTPServerResponse.status(HTTPStatus.NOT_MODIFIED)
                    .header("ETag", ETAG)
                    .header("Cache-Control", CACHE_CONTROL);
        }
        return HTTPServerResponse.status(HTTPStatus.OK, content())
                .header("Content-Type", "text/javascript; charset=utf-8")
                .header("ETag", ETAG)
                .header("Cache-Control", CACHE_CONTROL)
                .header("X-Content-Type-Options", "nosniff");
    }

    /** Read once from the classpath; a missing file is a packaging error. */
    private byte[] content() {
        var loaded = bytes;
        if (loaded == null) {
            val resource = PATH.substring(1);
            try (InputStream input = Objects.requireNonNull(HtmxScript.class.getClassLoader().getResourceAsStream(resource), resource)) {
                loaded = input.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            bytes = loaded;
        }
        return loaded;
    }
}
