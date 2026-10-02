package br.commons.web.server;

import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import br.commons.http.server.Ports;
import br.commons.web.ProblemDetail;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** O {@code Content-Type} da resposta de sucesso vem do {@code produces} registrado na rota. */
class WebServerProducesTest {

    private static Router router() {
        val router = new Router();
        router.get("/text", MediaType.TEXT_PLAIN, String.class,
                request -> WebResponse.ok("olá")).getOrThrow();
        router.get("/refused", MediaType.TEXT_PLAIN, String.class,
                request -> WebResponse.problem(ProblemDetail.of(HTTPStatus.UNAUTHORIZED))).getOrThrow();
        router.delete("/empty", MediaType.TEXT_PLAIN, Void.class,
                request -> WebResponse.noContent()).getOrThrow();
        return router;
    }

    @Test
    void successBodyTakesTheRegisteredProduces() throws IOException {
        withServer(port -> assertEquals("text/plain; charset=utf-8", open(port, "GET", "/text").getHeaderField("Content-Type")));
    }

    @Test
    void errorReturnedByTheHandlerKeepsItsOwnContentType() throws IOException {
        withServer(port -> {
            val response = open(port, "GET", "/refused");
            assertEquals(401, response.getResponseCode());
            assertEquals(WebResponse.JSON, response.getHeaderField("Content-Type"));
        });
    }

    @Test
    void responseWithoutBodyGetsNoContentType() throws IOException {
        withServer(port -> {
            val response = open(port, "DELETE", "/empty");
            assertEquals(204, response.getResponseCode());
            assertNull(response.getHeaderField("Content-Type"));
        });
    }

    private interface PortConsumer {
        void accept(int port) throws IOException;
    }

    private static void withServer(PortConsumer body) throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, router(), List.of());
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
