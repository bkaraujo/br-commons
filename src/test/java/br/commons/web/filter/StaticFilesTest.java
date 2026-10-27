package br.commons.web.filter;

import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StaticFilesTest {

    private static final HTTPHandler NEXT = _ -> HTTPServerResponse.status(HTTPStatus.ACCEPTED);

    @TempDir
    Path dir;

    private static HTTPServerRequest request(HTTPMethod method, String path) {
        return new HTTPServerRequest("", method, path, Map.of(), new byte[0]);
    }

    @Test
    void servesFromTheClasspathWithTypeAndCache() {
        val files = new StaticFiles("/static", "static-test", null, Duration.ofDays(1));
        val response = files.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT);
        assertEquals(200, response.status().code());
        assertEquals("text/css; charset=utf-8", response.header("Content-Type"));
        assertEquals("public, max-age=86400", response.header("Cache-Control"));
        assertEquals("nosniff", response.header("X-Content-Type-Options"));
        assertEquals("body{margin:0}\n", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void directoryWinsOverTheClasspathInDevelopment() throws Exception {
        Files.createDirectories(dir.resolve("css"));
        Files.writeString(dir.resolve("css/app.css"), "edited");
        Files.writeString(dir.resolve("blob"), "x");
        val files = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        val response = files.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT);
        assertEquals("edited", new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-cache", response.header("Cache-Control"));
        assertEquals("application/octet-stream", files.apply(request(HTTPMethod.GET, "/static/blob"), NEXT).header("Content-Type"));
    }

    @Test
    void refusesTraversalAndMissingFiles() {
        val files = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        for (val path : new String[] {"/static/../pom.xml", "/static/css%5Capp.css", "/static/none.js"}) {
            assertEquals(404, files.apply(request(HTTPMethod.GET, path), NEXT).status().code(), path);
        }
    }

    /** Regression: a directory answered 200 with the listing of its files (exploded classpath). */
    @Test
    void directoriesAreNotServed() throws Exception {
        val fromClasspath = new StaticFiles("/static/", "static-test", null, Duration.ZERO);
        assertEquals(404, fromClasspath.apply(request(HTTPMethod.GET, "/static/css"), NEXT).status().code());
        Files.createDirectories(dir.resolve("css"));
        val fromDisk = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        assertEquals(404, fromDisk.apply(request(HTTPMethod.GET, "/static/css"), NEXT).status().code());
        assertEquals(200, fromDisk.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT).status().code());
    }

    @Test
    void otherRequestsGoDownTheChain() {
        val files = new StaticFiles("/static/", "static-test", null, Duration.ZERO);
        assertEquals(202, files.apply(request(HTTPMethod.GET, "/login"), NEXT).status().code());
        assertEquals(202, files.apply(request(HTTPMethod.POST, "/static/css/app.css"), NEXT).status().code());
    }

    /** Regression (R8): every request read the whole file and there was no conditional request. */
    @Test
    void etagAnswersNotModified() throws Exception {
        val fromClasspath = new StaticFiles("/static/", "static-test", null, Duration.ZERO);
        val first = fromClasspath.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT);
        val etag = first.header("ETag");
        assertNotNull(etag);
        val again = new HTTPServerRequest("", HTTPMethod.GET, "/static/css/app.css", Map.of("if-none-match", etag), new byte[0]);
        assertEquals(304, fromClasspath.apply(again, NEXT).status().code());

        Files.createDirectories(dir.resolve("css"));
        Files.writeString(dir.resolve("css/app.css"), "a{}");
        val fromDisk = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        val diskTag = fromDisk.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT).header("ETag");
        assertNotNull(diskTag);
        val conditional = new HTTPServerRequest("", HTTPMethod.GET, "/static/css/app.css", Map.of("if-none-match", diskTag), new byte[0]);
        assertEquals(304, fromDisk.apply(conditional, NEXT).status().code());
    }

    private static String css(StaticFiles files) {
        return new String(files.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT).body(), StandardCharsets.UTF_8);
    }

    /** One request for a stylesheet split in files: its imports come inlined, relative to the importer, recursively. */
    @Test
    void importsAreInlinedInASingleResponse() throws Exception {
        Files.createDirectories(dir.resolve("css/screens"));
        Files.writeString(dir.resolve("css/app.css"), "@import \"screens/a.css\";\n@import url('screens/b.css');\nmain{}\n");
        Files.writeString(dir.resolve("css/screens/a.css"), "@import \"b.css\";\na{}\n");
        Files.writeString(dir.resolve("css/screens/b.css"), "b{}\n");
        val files = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        assertEquals("b{}\n\na{}\n\nb{}\n\nmain{}\n", css(files));
    }

    @Test
    void importsThatCannotBeInlinedStayAsWritten() throws Exception {
        val source = """
                @import "https://fonts.test/x.css";
                @import "../../secret.css";
                @import "none.css";
                @import "app.css";
                @import "w.css" screen;
                z{}
                """;
        Files.createDirectories(dir.resolve("css"));
        Files.writeString(dir.resolve("css/app.css"), source);
        val files = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        assertEquals(source, css(files));
    }

    /** The tag follows the imported files: editing one of them is not a 304 in development. */
    @Test
    void etagChangesWhenAnImportedFileChanges() throws Exception {
        Files.createDirectories(dir.resolve("css"));
        Files.writeString(dir.resolve("css/app.css"), "@import \"part.css\";");
        Files.writeString(dir.resolve("css/part.css"), "a{}");
        val files = new StaticFiles("/static/", "static-test", dir, Duration.ZERO);
        val before = files.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT).header("ETag");
        Files.writeString(dir.resolve("css/part.css"), "b{}");
        assertNotEquals(before, files.apply(request(HTTPMethod.GET, "/static/css/app.css"), NEXT).header("ETag"));
    }
}
