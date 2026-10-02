package br.commons.web.filter;

import br.commons.Strings;
import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPMethod;
import br.commons.http.message.HTTPRequest;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.HTTPFilter;
import br.commons.http.server.HTTPHandler;
import br.commons.http.server.message.HTTPServerResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Serves files under a URL prefix from the classpath or, in development, from a directory on disk
 * ({@code judas.web.static-dir}, spec §2.3), so CSS and JavaScript reload without a rebuild. A filter rather
 * than a route: the router's templates match one segment per parameter, and static paths are nested
 * ({@code /static/js/vendor/htmx.min.js}). Paths with {@code ..} or backslashes are refused.
 */
@NullMarked
public final class StaticFiles implements HTTPFilter {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json"),
            Map.entry("webmanifest", "application/manifest+json"),
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"));

    private final String prefix;
    private final String classpathRoot;
    private final @Nullable Path directory;
    private final String cacheControl;

    /**
     * @param prefix        URL prefix, e.g. {@code /static/}
     * @param classpathRoot resource directory, e.g. {@code static}
     * @param directory     directory that wins over the classpath (development), or {@code null}
     * @param maxAge        {@code Cache-Control: max-age}; zero sends {@code no-cache}
     */
    public StaticFiles(String prefix, String classpathRoot, @Nullable Path directory, Duration maxAge) {
        this.prefix = prefix.endsWith("/") ? prefix : prefix + "/";
        this.classpathRoot = classpathRoot;
        this.directory = directory == null ? null : directory.toAbsolutePath().normalize();
        this.cacheControl = maxAge.isZero() ? "no-cache" : "public, max-age=" + maxAge.toSeconds();
    }

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        if (request.method() != HTTPMethod.GET || !request.path().startsWith(prefix)) return next.handle(request);

        val name = request.path().substring(prefix.length());
        if (name.isEmpty() || name.contains("..") || name.contains("\\")) return HTTPServerResponse.status(HTTPStatus.NOT_FOUND);

        val bytes = read(name);
        if (bytes == null) return HTTPServerResponse.status(HTTPStatus.NOT_FOUND);
        return HTTPServerResponse.status(HTTPStatus.OK, bytes)
                .header("Content-Type", type(name))
                .header("Cache-Control", cacheControl)
                .header("X-Content-Type-Options", "nosniff");
    }

    /**
     * The bytes of a regular file; {@code null} for a missing name or a directory, which answer 404. A name found
     * in the development directory is answered from there, even when it is a directory.
     */
    private byte @Nullable [] read(String name) {
        try {
            if (directory != null) {
                val file = directory.resolve(name).normalize();
                if (file.startsWith(directory) && Files.exists(file)) {
                    return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
                }
            }
            return fromClasspath(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte @Nullable [] fromClasspath(String name) throws IOException {
        val resource = StaticFiles.class.getClassLoader().getResource(classpathRoot + "/" + name);
        if (resource == null || isDirectory(resource)) return null;
        try (InputStream input = resource.openStream()) {
            return input.readAllBytes();
        }
    }

    /**
     * Whether the classpath resource is a directory: the JDK opens one of an exploded classpath
     * ({@code target/classes}) as the listing of its files, which must not be served.
     */
    private static boolean isDirectory(URL resource) throws IOException {
        return switch (resource.getProtocol()) {
            case "file" -> Files.isDirectory(Path.of(URI.create(resource.toExternalForm())));
            case "jar" -> {
                val entry = ((JarURLConnection) resource.openConnection()).getJarEntry();
                yield entry == null || entry.isDirectory();
            }
            default -> false;
        };
    }

    private static String type(String name) {
        val dot = name.lastIndexOf('.');
        return TYPES.getOrDefault(dot < 0 ? "" : Strings.lower(name.substring(dot + 1)), "application/octet-stream");
    }
}
