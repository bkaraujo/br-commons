package br.commons.http.server;

import br.commons.http.MediaType;
import br.commons.http.message.HTTPMethod;
import br.commons.http.message.HTTPRequest;
import br.commons.http.server.message.HTTPServerRequest;
import br.commons.http.server.message.HTTPServerResponse;
import br.commons.logger.MDC;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HTTPServerRequestTest {

    @Test
    void headerNamesAreCaseInsensitive() {
        val request = new HTTPServerRequest("", HTTPMethod.GET, "/x", Map.of(), Map.of("X-Request-ID", "abc"), new byte[0]);
        assertEquals("abc", request.header("x-request-id"));
        assertEquals("abc", request.header("X-REQUEST-ID"));
        assertFalse(request.hasBody());
        assertNull(request.mediaType());
    }

    /** Regression (S1): the client's X-REQUEST-ID reached the MDC and the logs unvalidated (log injection). */
    @Test
    void clientRequestIdIsValidatedNotTrusted() {
        assertEquals("abc-123", HTTPServerRequest.clientRequestId("abc-123"));
        assertEquals("", HTTPServerRequest.clientRequestId(null));
        assertEquals("", HTTPServerRequest.clientRequestId("  "));
        assertEquals("", HTTPServerRequest.clientRequestId("a\r\nFAKE log line"));
        assertEquals("", HTTPServerRequest.clientRequestId("x".repeat(65)));
        assertEquals("", HTTPServerRequest.clientRequestId("<script>"));

        val request = new HTTPServerRequest("internal", HTTPMethod.GET, "/x", Map.of(), Map.of("X-Request-ID", "abc-123"), new byte[0]);
        assertEquals("internal", request.requestId());
        assertEquals("abc-123", request.clientRequestId());
    }

    @Test
    void responseTakesTheRequestIdFromTheMdcAndIsEmptyOutsideARequest() {
        assertEquals("", HTTPServerResponse.noContent().requestId());

        MDC.push(HTTPRequest.REQUEST_ID, "req-9");
        try {
            assertEquals("req-9", HTTPServerResponse.noContent().requestId());
        } finally {
            MDC.pop(HTTPRequest.REQUEST_ID);
        }
    }

    @Test
    void mediaTypeComesFromTheContentTypeHeader() {
        val request = new HTTPServerRequest("", HTTPMethod.POST, "/x", Map.of(), Map.of("content-type", "application/json; charset=utf-8"), new byte[0]);
        assertEquals(MediaType.APPLICATION_JSON, request.mediaType());
    }

    @Test
    void parametersAreReadOnlyAndAttributesAreNot() {
        val request = new HTTPServerRequest("", HTTPMethod.GET, "/x", Map.of("since", "10"), Map.of(), new byte[0]);
        assertEquals("10", request.parameter("since"));
        assertThrows(UnsupportedOperationException.class, () -> request.parameters().put("since", "0"));

        request.attribute("resolved", 42);
        assertEquals(42, request.attribute("resolved"));
    }
}
