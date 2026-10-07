package br.commons.protocol.http.server;

import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.HTTPServer;
import br.commons.protocol.http.server.MultipartParser;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The server streams multipart uploads to disk, applies their own limit and deletes the files afterwards. */
class HTTPServerMultipartTest {

    private static final String BOUNDARY = "xyzBOUNDARY";

    @TempDir
    Path dir;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Path> seen = new ArrayList<>();
    private HTTPServer server;
    private String base;

    @BeforeEach
    void start() {
        HTTPHandler handler = request -> {
            val upload = request.multipart();
            upload.files().forEach(file -> seen.add(file.path()));
            val text = "body=" + request.body().length + " files=" + upload.files().size() + " password=" + upload.field("password")
                    + " exists=" + upload.files().stream().allMatch(file -> Files.exists(file.path()));
            return HTTPServerResponse.status(HTTPStatus.OK, text.getBytes(StandardCharsets.UTF_8));
        };
        server = new HTTPServer(0, List.of(), handler, failure -> HTTPServerResponse.status(HTTPStatus.INTERNAL_SERVER_ERROR),
                10, new MultipartParser.Limits(dir, 1000, 500));
        server.start().getOrThrow();
        base = "http://localhost:" + server.port();
    }

    @AfterEach
    void stop() {
        server.stop(Duration.ZERO);
    }

    private HttpResponse<String> post(String contentType, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/import")).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String multipart(String fileContent) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\nsecret\r\n"
                + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.pdf\"\r\n\r\n" + fileContent + "\r\n"
                + "--" + BOUNDARY + "--\r\n";
    }

    @Test
    void streamsTheUploadAboveTheMemoryBodyLimitAndDeletesItAfterwards() throws Exception {
        val response = post("multipart/form-data; boundary=" + BOUNDARY, multipart("%PDF-" + "x".repeat(100)));
        assertEquals(200, response.statusCode());
        assertEquals("body=0 files=1 password=secret exists=true", response.body());
        assertEquals(1, seen.size());
        assertFalse(Files.exists(seen.getFirst()), "the temporary file is gone after the request");
    }

    @Test
    void aboveTheUploadLimitAnswers413() throws Exception {
        assertEquals(413, post("multipart/form-data; boundary=" + BOUNDARY, multipart("x".repeat(600))).statusCode());
    }

    @Test
    void aMalformedUploadAnswers400() throws Exception {
        assertEquals(400, post("multipart/form-data; boundary=" + BOUNDARY, "garbage without delimiter").statusCode());
    }

    @Test
    void otherBodiesKeepTheMemoryLimit() throws Exception {
        assertEquals(413, post("text/plain", "x".repeat(11)).statusCode());
        assertEquals("body=5 files=0 password=null exists=true", post("text/plain", "hello").body());
    }
}
