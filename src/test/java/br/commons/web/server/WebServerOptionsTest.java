package br.commons.web.server;

import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.Ports;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@code OPTIONS} sai do cache de rotas montado no registro, sem cadeia de filtros nem handler. */
class WebServerOptionsTest {

    private final AtomicInteger handlerCalls = new AtomicInteger();

    private Router router() {
        val router = new Router();
        router.register(Route.get("/api/items/{id}", MediaType.APPLICATION_JSON, Void.class, true, request -> {
            handlerCalls.incrementAndGet();
            return WebResponse.noContent();
        }));
        router.register(Route.patch("/api/items/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, request -> WebResponse.noContent()));
        return router;
    }

    @Test
    void optionsAnswersAllowBeforeFiltersThatWouldReject() throws Exception {
        HTTPFilter rejectsEverything = (request, next) -> HTTPServerResponse.status(HTTPStatus.UNAUTHORIZED);
        withServer(List.of(rejectsEverything), port -> {
            val known = open(port, "OPTIONS", "/api/items/42");
            assertEquals(204, known.getResponseCode());
            assertEquals("GET, PATCH, OPTIONS", known.getHeaderField("Allow"));

            val unknown = open(port, "OPTIONS", "/api/nothing");
            assertEquals(404, unknown.getResponseCode());
            assertNull(unknown.getHeaderField("Allow"));
        });
        assertEquals(0, handlerCalls.get());
    }

    @Test
    void methodNotAllowedCarriesTheSameAllow() throws Exception {
        withServer(List.of(), port -> {
            val response = open(port, "DELETE", "/api/items/42");
            assertEquals(405, response.getResponseCode());
            assertEquals("GET, PATCH, OPTIONS", response.getHeaderField("Allow"));
            assertEquals(WebResponse.JSON, response.getHeaderField("Content-Type"));
        });
    }

    @Test
    void failureOutsideTheRouteAnswersProblemDetail() throws Exception {
        HTTPFilter explodes = (request, next) -> { throw new IllegalStateException("boom"); };
        withServer(List.of(explodes), port -> {
            val response = open(port, "GET", "/api/items/42");
            assertEquals(500, response.getResponseCode());
            assertEquals(WebResponse.JSON, response.getHeaderField("Content-Type"));
        });
    }

    private interface PortConsumer {
        void accept(int port) throws IOException;
    }

    private void withServer(List<HTTPFilter> filters, PortConsumer body) throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, router(), filters);
        server.start().getOrThrow();
        try {
            body.accept(port);
        } finally {
            server.stop();
        }
    }

    private static HttpURLConnection open(int port, String method, String path) throws IOException {
        val connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        connection.setRequestMethod(method);
        return connection;
    }
}
