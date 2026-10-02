package br.commons.web.server;

import br.commons.http.MediaType;
import br.commons.http.message.HTTPMethod;
import br.commons.http.server.message.HTTPServerRequest;
import br.commons.web.router.Route;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The body is read using the {@link MediaType} of the route record, never by the client's {@code Content-Type}. */
class WebRequestTest {

    private record Payload(String name) {}

    private static Route post(MediaType consumes, Class<?> requestType) {
        return new Router().post("/api/x/{id}", consumes, MediaType.APPLICATION_JSON, requestType, Void.class,
                r -> WebResponse.noContent()).getOrThrow();
    }

    private static WebRequest request(Route route, String body, Map<String, String> headers) {
        val raw = new HTTPServerRequest("", route.method(), "/api/x/7", Map.of(), headers, body.getBytes(StandardCharsets.UTF_8));
        return new WebRequest(raw, route, Map.of("id", "7"));
    }

    @Test
    void jsonRouteReadsJsonEvenWhenTheClientClaimsOtherwise() {
        val request = request(post(MediaType.APPLICATION_JSON, Payload.class), "{\"name\":\"ana\"}", Map.of("Content-Type", "text/plain"));

        assertEquals(new Payload("ana"), request.body(Payload.class).getOrThrow());
        assertEquals("7", request.pathParam("id").getOrThrow());
        assertTrue(request.pathParam("absent").isFailure());
    }

    @Test
    void textRouteReadsTheRawStringOnly() {
        val request = request(post(MediaType.TEXT_PLAIN, String.class), "{\"name\":\"ana\"}", Map.of());

        assertEquals("{\"name\":\"ana\"}", request.body(String.class).getOrThrow());
        assertTrue(request.body(Payload.class).isFailure());
    }

    @Test
    void mediaTypeWithoutReaderFails() {
        val request = request(post(MediaType.APPLICATION_XML, Payload.class), "<name>ana</name>", Map.of());

        assertTrue(request.body(Payload.class).isFailure());
    }

    @Test
    void routeWithoutBodyFails() {
        val get = new Router().get("/api/x/{id}", MediaType.APPLICATION_JSON, Payload.class, r -> WebResponse.noContent()).getOrThrow();

        assertNull(get.consumes());
        assertTrue(request(get, "{\"name\":\"ana\"}", Map.of()).body(Payload.class).isFailure());
    }

    @Test
    void requestIdIsTheRawRequestOne() {
        val route = post(MediaType.APPLICATION_JSON, Payload.class);
        val raw = new HTTPServerRequest("", HTTPMethod.POST, "/api/x/7", Map.of(), Map.of(), new byte[0]);

        assertEquals(raw.requestId(), new WebRequest(raw, route, Map.of()).requestId());
    }

    @Test
    void attributesAreSharedWithTheRawRequest() {
        val route = post(MediaType.APPLICATION_JSON, Payload.class);
        val raw = new HTTPServerRequest("", HTTPMethod.POST, "/api/x/7", Map.of(), Map.of(), new byte[0]);
        val request = new WebRequest(raw, route, Map.of());

        request.attribute("seen", true);
        assertEquals(true, raw.attribute("seen"));
    }
}
