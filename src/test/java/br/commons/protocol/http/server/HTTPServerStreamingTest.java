package br.commons.protocol.http.server;

import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The streaming primitive behind Phase 3's SSE: {@code HTTPServerResponse.stream} keeps the
 * connection open and writes frames incrementally, without materializing any body.
 */
class HTTPServerStreamingTest {

    @Test
    void streamsMultipleChunksOverTheSameConnection() throws Exception {
        val port = Ports.free();
        val clientConnected = new CountDownLatch(1);

        HTTPHandler handler = request -> HTTPServerResponse.stream(out -> {
            try {
                clientConnected.countDown();
                for (int i = 1; i <= 3; i++) {
                    out.write(("data: chunk-" + i + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception ignored) {
                // Client disconnected in the middle — SSE routine, not a test error.
            }
        }).header("Content-Type", "text/plain");

        val server = new HTTPServer(port, List.of(), handler);
        server.start().getOrThrow();
        try {
            val connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/stream").toURL().openConnection();
            connection.setRequestMethod("GET");

            assertEquals(200, connection.getResponseCode());
            assertEquals("text/plain", connection.getHeaderField("Content-Type"));

            val received = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    received.append(line).append('\n');
                }
            }

            assertTrue(clientConnected.await(2, TimeUnit.SECONDS));
            assertTrue(received.toString().contains("chunk-1"));
            assertTrue(received.toString().contains("chunk-3"));
        } finally {
            server.stop();
        }
    }
}
