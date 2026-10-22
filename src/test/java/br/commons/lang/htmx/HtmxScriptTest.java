package br.commons.lang.htmx;

import br.commons.Logger;
import br.commons.logger.channel.ConsoleChannel;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmxScriptTest {

    private static final HTTPHandler NEXT = _ -> HTTPServerResponse.status(HTTPStatus.ACCEPTED);
    private static final String RESOURCE = HtmxScript.PATH.substring(1);
    private static final String ETAG = "\"htmx-" + HtmxScript.VERSION + "\"";

    private final List<String> captured = new CopyOnWriteArrayList<>();

    @BeforeEach
    void capture() {
        Logger.channel(captured::add);
    }

    @AfterEach
    void restore() {
        Logger.channel(new ConsoleChannel());
    }

    private static HTTPServerRequest request(HTTPMethod method, String path, Map<String, String> headers) {
        return new HTTPServerRequest("", method, path, headers, new byte[0]);
    }

    @Test
    void servesTheBundledScriptWithImmutableCache() throws IOException {
        val response = new HtmxScript().apply(request(HTTPMethod.GET, HtmxScript.PATH, Map.of()), NEXT);
        assertEquals(200, response.status().code());
        assertEquals("text/javascript; charset=utf-8", response.header("Content-Type"));
        assertEquals(ETAG, response.header("ETag"));
        assertEquals("public, max-age=31536000, immutable", response.header("Cache-Control"));
        assertEquals("nosniff", response.header("X-Content-Type-Options"));
        try (val input = Objects.requireNonNull(HtmxScriptTest.class.getClassLoader().getResourceAsStream(RESOURCE))) {
            assertArrayEquals(input.readAllBytes(), response.body());
        }
    }

    @Test
    void sameEtagIsNotModified() {
        val script = new HtmxScript();
        val response = script.apply(request(HTTPMethod.GET, HtmxScript.PATH, Map.of("If-None-Match", ETAG)), NEXT);
        assertEquals(304, response.status().code());
        assertEquals(ETAG, response.header("ETag"));
        assertEquals(0, response.body().length);
        assertEquals(200, script.apply(request(HTTPMethod.GET, HtmxScript.PATH, Map.of("If-None-Match", "\"htmx-0\"")), NEXT)
                .status().code());
    }

    @Test
    void otherRequestsGoToTheNextHandler() {
        val script = new HtmxScript();
        assertEquals(202, script.apply(request(HTTPMethod.POST, HtmxScript.PATH, Map.of()), NEXT).status().code());
        assertEquals(202, script.apply(request(HTTPMethod.GET, "/app.js", Map.of()), NEXT).status().code());
    }

    /** A build without the {@code .min.js} (packaging error) answers 500 and logs why, instead of throwing. */
    @Test
    void missingScriptIsServerErrorAndLogged() throws ReflectiveOperationException {
        val script = (HTTPFilter) new WithoutScript().loadClass(HtmxScript.class.getName())
                .getDeclaredConstructor().newInstance();
        val response = script.apply(request(HTTPMethod.GET, HtmxScript.PATH, Map.of()), NEXT);
        assertEquals(500, response.status().code());
        assertTrue(captured.stream().anyMatch(line -> line.contains(RESOURCE)), captured::toString);
    }

    /** Defines its own {@link HtmxScript}, whose class loader does not find the script; the rest comes from the parent. */
    private static final class WithoutScript extends ClassLoader {

        WithoutScript() {
            super(HtmxScriptTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(HtmxScript.class.getName())) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                val loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (val input = Objects.requireNonNull(getParent().getResourceAsStream(name.replace('.', '/') + ".class"))) {
                    val bytes = input.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return name.equals(RESOURCE) ? null : super.getResourceAsStream(name);
        }
    }
}
