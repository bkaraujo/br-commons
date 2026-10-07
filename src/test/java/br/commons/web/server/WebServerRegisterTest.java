package br.commons.web.server;

import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.server.Ports;
import br.commons.web.WebResponse;
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

    private static final WebResource PING = router ->
            router.get("/ping", MediaType.TEXT_PLAIN, String.class, request -> WebResponse.ok("pong")).getOrThrow();

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

    private static HttpURLConnection open(int port, String method, String path) throws IOException {
        val connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        connection.setRequestMethod(method);
        return connection;
    }
}
