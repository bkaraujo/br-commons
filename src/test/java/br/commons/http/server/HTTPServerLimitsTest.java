package br.commons.http.server;

import br.commons.http.Cookie;
import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPMethod;
import br.commons.http.server.message.HTTPServerRequest;
import br.commons.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** JUDAS additions to the br-community server: body limit, unknown methods, cookies, graceful stop. */
class HTTPServerLimitsTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private HTTPServer server;
    private String base;

    @BeforeEach
    void start() {
        HTTPHandler handler = request -> HTTPServerResponse.status(HTTPStatus.OK,
                        ("cookie=" + request.cookie("sid") + " body=" + request.body().length).getBytes(StandardCharsets.UTF_8))
                .cookie(Cookie.session("sid", "abc"))
                .cookie(Cookie.expired("old"));
        server = new HTTPServer(0, List.of(), handler, failure -> HTTPServerResponse.status(HTTPStatus.INTERNAL_SERVER_ERROR), 10);
        server.start().getOrThrow();
        base = "http://localhost:" + server.port();
    }

    @AfterEach
    void stop() {
        server.stop(Duration.ofSeconds(1));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readsCookiesAndWritesSeveralSetCookieHeaders() throws Exception {
        val response = send(HttpRequest.newBuilder(URI.create(base + "/")).header("Cookie", "x=1; sid=s3cr3t; sid=other"));
        assertEquals("cookie=s3cr3t body=0", response.body());
        assertEquals(List.of("sid=abc; Path=/; HttpOnly; Secure; SameSite=Strict",
                        "old=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict"),
                response.headers().allValues("Set-Cookie"));
    }

    @Test
    void bodyUpToTheLimitPassesAndLargerGets413() throws Exception {
        val ok = send(HttpRequest.newBuilder(URI.create(base + "/")).POST(HttpRequest.BodyPublishers.ofString("0123456789")));
        assertEquals("cookie=null body=10", ok.body());
        val tooLarge = send(HttpRequest.newBuilder(URI.create(base + "/")).POST(HttpRequest.BodyPublishers.ofString("0123456789X")));
        assertEquals(413, tooLarge.statusCode());
    }

    /** Regression: {@code limit + 1} overflowed for {@code Long.MAX_VALUE} (no limit) and every body was read empty. */
    @Test
    void readsTheWholeBodyWhenThereIsNoLimit() throws Exception {
        val body = "title=abc".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(body, HTTPServer.readBody(new ByteArrayInputStream(body), Long.MAX_VALUE));
        assertArrayEquals(body, HTTPServer.readBody(new ByteArrayInputStream(body), body.length));
        assertNull(HTTPServer.readBody(new ByteArrayInputStream(body), body.length - 1));
    }

    /** Regression: a body over the largest array was truncated and passed on as complete when there was no limit. */
    @Test
    void aBodyOverTheArrayCapIsTooLargeEvenWithoutLimit() throws Exception {
        val body = "0123456789".getBytes(StandardCharsets.UTF_8);
        assertNull(HTTPServer.readBody(new ByteArrayInputStream(body), Long.MAX_VALUE, 8));
        assertArrayEquals(body, HTTPServer.readBody(new ByteArrayInputStream(body), Long.MAX_VALUE, 10));
    }

    /** Regression: malformed percent-encoding in the query failed the request with a 500 and a fatal log. */
    @Test
    void malformedQueryGets400() throws Exception {
        assertTrue(HTTPServer.parseQuery("q=%zz").isFailure());
        assertTrue(HTTPServer.parseQuery("q=100%").isFailure());
        assertTrue(HTTPServer.parseQuery("%zz=1").isFailure());
        assertEquals(Map.of("q", "a b", "x", "é"), HTTPServer.parseQuery("q=a%20b&x=%C3%A9&flag").getOrThrow());
        for (val query : List.of("q=%zz", "q=100%")) {
            try (val socket = new Socket("localhost", server.port())) {
                socket.getOutputStream().write(("GET /?" + query + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                val status = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).lines().findFirst();
                assertEquals(Optional.of("HTTP/1.1 400 Bad Request"), status, query);
            }
        }
    }

    @Test
    void responseEchoesTheClientRequestIdOrAGeneratedOne() throws Exception {
        val echoed = send(HttpRequest.newBuilder(URI.create(base + "/")).header("X-Request-ID", "req-42"));
        assertEquals("req-42", echoed.headers().firstValue("X-REQUEST-ID").orElseThrow());

        val generated = send(HttpRequest.newBuilder(URI.create(base + "/")));
        assertFalse(generated.headers().firstValue("X-REQUEST-ID").orElseThrow().isBlank());
    }

    @Test
    void unknownMethodGets405() throws Exception {
        val head = send(HttpRequest.newBuilder(URI.create(base + "/")).method("HEAD", HttpRequest.BodyPublishers.noBody()));
        assertEquals(405, head.statusCode());
    }

    @Test
    void requestWithoutCookieHeaderHasNoCookie() {
        val request = new HTTPServerRequest("", HTTPMethod.GET, "/", java.util.Map.of(), java.util.Map.of(), new byte[0]);
        assertNull(request.cookie("sid"));
        Function<String, String> header = value -> new HTTPServerRequest("", HTTPMethod.GET, "/", java.util.Map.of(),
                java.util.Map.of("Cookie", value), new byte[0]).cookie("sid");
        assertNull(header.apply("broken; =x; other=1"));
    }

    /** Regression (R10): a sub-second grace was truncated to zero and cut the request in flight. */
    @Test
    void subSecondGraceStillWaitsForTheRequestInFlight() throws Exception {
        val entered = new java.util.concurrent.CountDownLatch(1);
        HTTPHandler slow = request -> {
            entered.countDown();
            br.commons.Threads.sleep(300);
            return HTTPServerResponse.status(HTTPStatus.OK, "done".getBytes(StandardCharsets.UTF_8));
        };
        val graceful = new HTTPServer(0, List.of(), slow, failure -> HTTPServerResponse.status(HTTPStatus.INTERNAL_SERVER_ERROR), 10);
        graceful.start().getOrThrow();
        val pending = client.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:" + graceful.port() + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));

        graceful.stop(Duration.ofMillis(500));

        val response = pending.get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertEquals("done", response.body());
    }

    /** Regression (R1): the constructors without a limit accepted any body size. */
    @Test
    void defaultBodyLimitIsOneMebibyteAndConfigurable() {
        val previous = System.getProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY);
        try {
            System.clearProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY);
            assertEquals(1024 * 1024, HTTPServer.defaultMaxBodySize());
            System.setProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY, "2048");
            assertEquals(2048, HTTPServer.defaultMaxBodySize());
            System.setProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY, "lots");
            assertEquals(1024 * 1024, HTTPServer.defaultMaxBodySize());
        } finally {
            if (previous == null) System.clearProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY);
            else System.setProperty(HTTPServer.MAX_BODY_SIZE_PROPERTY, previous);
        }
    }
}
