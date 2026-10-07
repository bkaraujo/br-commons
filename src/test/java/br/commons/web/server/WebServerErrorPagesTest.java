package br.commons.web.server;

import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.server.Ports;
import br.commons.web.ProblemDetail;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Página de erro registrada por status: HTML para navegador, {@code ProblemDetail} para API. */
class WebServerErrorPagesTest {

    private static final String BROWSER = "text/html,application/xhtml+xml,*/*;q=0.8";

    private static Router router() {
        val router = new Router();
        router.get("/boom", MediaType.APPLICATION_JSON, String.class, request -> {
            throw new IllegalStateException("boom");
        }).getOrThrow();
        router.get("/conflict", MediaType.APPLICATION_JSON, String.class,
                request -> WebResponse.problem(ProblemDetail.of(HTTPStatus.CONFLICT))).getOrThrow();
        router.post("/only-post", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, String.class, String.class,
                request -> WebResponse.ok("ok")).getOrThrow();
        return router;
    }

    @Test
    void browserGetsTheDefault404Page() throws IOException {
        withServer(server -> server.defaultErrorPages().getOrThrow(), port -> {
            val response = open(port, "/missing", BROWSER);
            assertEquals(404, response.getResponseCode());
            assertEquals("text/html; charset=utf-8", response.getHeaderField("Content-Type"));
            assertTrue(body(response.getErrorStream()).contains("<h1>404</h1>"));
        });
    }

    @Test
    void apiClientKeepsTheProblemDetail() throws IOException {
        withServer(server -> server.defaultErrorPages().getOrThrow(), port -> {
            val response = open(port, "/missing", "application/json");
            assertEquals(404, response.getResponseCode());
            assertEquals(WebResponse.JSON, response.getHeaderField("Content-Type"));
        });
    }

    @Test
    void exceptionBecomesThe500Page() throws IOException {
        withServer(server -> server.defaultErrorPages().getOrThrow(), port -> {
            val response = open(port, "/boom", BROWSER);
            assertEquals(500, response.getResponseCode());
            assertTrue(body(response.getErrorStream()).contains("<h1>500</h1>"));
        });
    }

    @Test
    void anyStatusCanHaveAPage() throws IOException {
        withServer(server -> server.errorPage(HTTPStatus.CONFLICT, (request, status) -> "conflito em " + request.path()).getOrThrow(), port -> {
            val response = open(port, "/conflict", BROWSER);
            assertEquals(409, response.getResponseCode());
            assertEquals("conflito em /conflict", body(response.getErrorStream()));
        });
    }

    @Test
    void pageKeepsTheOriginalHeaders() throws IOException {
        withServer(server -> server.errorPage(HTTPStatus.METHOD_NOT_ALLOWED, (request, status) -> "405").getOrThrow(), port -> {
            val response = open(port, "/only-post", BROWSER);
            assertEquals(405, response.getResponseCode());
            assertTrue(response.getHeaderField("Allow").contains("POST"));
            assertEquals("text/html; charset=utf-8", response.getHeaderField("Content-Type"));
        });
    }

    @Test
    void classpathPageReplacesTheDefault() throws IOException {
        withServer(server -> {
            server.defaultErrorPages().getOrThrow();
            server.errorPage(HTTPStatus.NOT_FOUND, "/errors/404.html").getOrThrow();
        }, port -> {
            val response = open(port, "/missing", BROWSER);
            assertEquals(404, response.getResponseCode());
            assertTrue(body(response.getErrorStream()).contains("Página 404 do classpath"));
        });
    }

    @Test
    void missingClasspathPageFails() {
        val server = new WebServer(Ports.free(), router(), List.of());
        assertTrue(server.errorPage(HTTPStatus.NOT_FOUND, "/errors/none.html").isFailure());
    }

    @Test
    void registrationAfterStartFails() throws IOException {
        val server = new WebServer(Ports.free(), router(), List.of());
        server.start().getOrThrow();
        try {
            assertTrue(server.defaultErrorPages().isFailure());
        } finally {
            server.stop();
        }
    }

    private interface PortConsumer {
        void accept(int port) throws IOException;
    }

    private interface Setup {
        void accept(WebServer server);
    }

    private static void withServer(Setup setup, PortConsumer body) throws IOException {
        val port = Ports.free();
        val server = new WebServer(port, router(), List.of());
        setup.accept(server);
        server.start().getOrThrow();
        try {
            body.accept(port);
        } finally {
            server.stop();
        }
    }

    private static HttpURLConnection open(int port, String path, String accept) throws IOException {
        val connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        connection.setRequestProperty("Accept", accept);
        return connection;
    }

    private static String body(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
