package br.commons.web.server;

import br.commons.Result;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.server.Ports;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code WebServer.register(WebResource)} delega ao {@code WebDispatcher} interno, que aciona a rota. */
class WebServerRegisterTest {

    private static final WebResource PING = () ->
            List.of(Route.get("/ping", MediaType.TEXT_PLAIN, String.class, true, request -> WebResponse.ok("pong")));

    @Test
    void registeredResourceAnswersOverRealHttp() throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, new Router(), List.of());
        server.register(PING);
        server.start().getOrThrow();
        try {
            val response = open(port, "GET", "/ping");
            assertEquals(200, response.getResponseCode());
        } finally {
            server.stop();
        }
    }

    /** A route that fails to build or register is logged; the others of the resource and the server carry on. */
    @Test
    void failingRoutesDoNotStopTheServerNorTheOtherRoutes() throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, new Router((method, template) ->
                template.equals("/refused") ? Result.failure("refused") : Result.success()), List.of());
        server.register(() -> List.of(
                Route.get("/bad/{order-id}", MediaType.TEXT_PLAIN, String.class, true, request -> WebResponse.ok("bad")),
                Route.get("/refused", MediaType.TEXT_PLAIN, String.class, true, request -> WebResponse.ok("refused")),
                Route.get("/ping", MediaType.TEXT_PLAIN, String.class, true, request -> WebResponse.ok("pong"))));
        server.start().getOrThrow();
        try {
            assertEquals(200, open(port, "GET", "/ping").getResponseCode());
            assertEquals(404, open(port, "GET", "/bad/1").getResponseCode());
            assertEquals(404, open(port, "GET", "/refused").getResponseCode());
        } finally {
            server.stop();
        }
    }

    /** A resource is registered after the start too, but the router is frozen: nothing enters, nothing is thrown. */
    @Test
    void registrationAfterTheStartIsRefusedWithoutThrowing() throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, new Router(), List.of());
        server.start().getOrThrow();
        try {
            server.register(PING);
            assertEquals(404, open(port, "GET", "/ping").getResponseCode());
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
