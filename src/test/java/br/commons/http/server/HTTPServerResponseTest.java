package br.commons.http.server;

import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HTTPServerResponseTest {

    @Test
    void headersAreCaseInsensitiveAndReadOnlyFromOutside() {
        HTTPResponse response = HTTPServerResponse.noContent().header("X-Request-ID", "abc");
        assertEquals(HTTPStatus.NO_CONTENT, response.status());
        assertEquals("abc", response.header("x-request-id"));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("allow", "GET"));
        assertNull(response.mediaType());
    }

    @Test
    void mediaTypeWritesTheContentTypeHeader() {
        val response = HTTPServerResponse.status(HTTPStatus.OK, new byte[] {1}).mediaType(MediaType.TEXT_PLAIN);
        assertEquals("text/plain; charset=utf-8", response.header("Content-Type"));
        assertEquals(MediaType.TEXT_PLAIN, response.mediaType());
    }

    @Test
    void onlyStreamingResponsesHaveAWriter() {
        val fixed = HTTPServerResponse.status(HTTPStatus.OK, new byte[] {1});
        assertFalse(fixed.isStreaming());
        assertTrue(fixed.streamWriter().isFailure());
        assertTrue(fixed.hasBody());

        val streaming = HTTPServerResponse.stream(out -> { });
        assertTrue(streaming.isStreaming());
        assertTrue(streaming.streamWriter().isSuccess());
        assertFalse(streaming.hasBody());
    }

    /** Regression (R2): CR/LF in a header or cookie split the response (header injection). */
    @Test
    void lineBreaksInHeadersAndCookiesAreRefused() {
        val response = HTTPServerResponse.status(HTTPStatus.OK);
        assertThrows(IllegalArgumentException.class, () -> response.header("HX-Location", "/a\r\nSet-Cookie: x=1"));
        assertThrows(IllegalArgumentException.class, () -> response.header("X-A\nB", "v"));
        assertThrows(IllegalArgumentException.class, () -> br.commons.http.Cookie.session("sid", "a\r\nb"));
        assertThrows(IllegalArgumentException.class, () -> br.commons.http.Cookie.session("sid", "a; Domain=evil"));
        assertEquals("/ok", response.header("HX-Location", "/ok").header("HX-Location"));
    }
}
