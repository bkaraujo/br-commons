package br.commons.web.filter;

import br.commons.Strings;
import br.commons.locale.i18n.Errors;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.protocol.http.server.HTTPFilter;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves files under a URL prefix from the classpath or, in development, from a directory on disk
 * (directory chosen by the application), so CSS and JavaScript reload without a rebuild. A filter rather
 * than a route: the router's templates match one segment per parameter, and static paths are nested
 * ({@code /static/js/vendor/htmx.min.js}). Paths with {@code ..} or backslashes are refused.
 *
 * <p>A stylesheet is served with its {@code @import "x.css";} lines replaced by the imported file (relative to the
 * importer, recursively): the source stays in several files, the client makes one request. An import that is remote,
 * unsafe, missing or circular, or carries a media condition, is left as written.
 *
 * <p>Every file carries an {@code ETag} and {@code If-None-Match} answers 304. Classpath files are immutable: read
 * and hashed once per name. Development files use size + modification time, so a 304 never reads the file; a
 * stylesheet is composed and hashed on each request instead, so a change in an imported file changes its tag.
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
    private static final Pattern IMPORT = Pattern.compile("@import\\s+(?:url\\(\\s*)?[\"']([^\"']+)[\"']\\s*\\)?\\s*;");

    private final Map<String, Entry> classpathCache = new ConcurrentHashMap<>();

    /** A servable file. {@code bytes} is {@code null} when only the tag was needed (development directory, 304). */
    @NullMarked
    private record Entry(String etag, @Nullable Path file, byte @Nullable [] bytes) {
        byte[] content() throws IOException {
            if (bytes != null) return bytes;
            return Files.readAllBytes(Objects.requireNonNull(file));
        }
    }

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
        if (!safe(name)) return HTTPServerResponse.status(HTTPStatus.NOT_FOUND);

        try {
            val entry = find(name);
            if (entry == null) return HTTPServerResponse.status(HTTPStatus.NOT_FOUND);
            return respond(request, name, entry);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean safe(String name) {
        return !name.isEmpty() && !name.contains("..") && !name.contains("\\");
    }

    private HTTPResponse respond(HTTPRequest request, String name, Entry entry) throws IOException {
        if (entry.etag().equals(request.header("If-None-Match"))) {
            return HTTPServerResponse.status(HTTPStatus.NOT_MODIFIED)
                    .header("ETag", entry.etag())
                    .header("Cache-Control", cacheControl);
        }
        return HTTPServerResponse.status(HTTPStatus.OK, entry.content())
                .header("Content-Type", type(name))
                .header("ETag", entry.etag())
                .header("Cache-Control", cacheControl)
                .header("X-Content-Type-Options", "nosniff");
    }

    /**
     * A regular file; {@code null} for a missing name or a directory, which answer 404. A name found in the
     * development directory is answered from there, even when it is a directory.
     */
    private @Nullable Entry find(String name) throws IOException {
        if (name.endsWith(".css")) return stylesheet(name);
        if (directory != null) {
            val file = directory.resolve(name).normalize();
            if (file.startsWith(directory) && Files.exists(file)) {
                if (!Files.isRegularFile(file)) return null;
                val etag = "\"" + Long.toHexString(Files.size(file)) + "-"
                        + Long.toHexString(Files.getLastModifiedTime(file).toMillis()) + "\"";
                return new Entry(etag, file, null);
            }
        }
        val cached = classpathCache.get(name);
        if (cached != null) return cached;
        val bytes = fromClasspath(name);
        if (bytes == null) return null;
        val entry = new Entry("\"" + sha256(bytes) + "\"", null, bytes);
        classpathCache.putIfAbsent(name, entry);
        return entry;
    }

    /** The stylesheet with its imports inlined; cached only when it comes from the classpath alone. */
    private @Nullable Entry stylesheet(String name) throws IOException {
        val cached = classpathCache.get(name);
        if (cached != null) return cached;
        val source = source(name);
        if (source == null) return null;
        val bytes = inline(name, new String(source, StandardCharsets.UTF_8), new HashSet<>(Set.of(name)))
                .getBytes(StandardCharsets.UTF_8);
        val entry = new Entry("\"" + sha256(bytes) + "\"", null, bytes);
        if (directory == null) classpathCache.putIfAbsent(name, entry);
        return entry;
    }

    /** {@code css} (the content of {@code name}) with each import it can resolve replaced by the imported content. */
    private String inline(String name, String css, Set<String> chain) throws IOException {
        val matcher = IMPORT.matcher(css);
        val result = new StringBuilder();
        while (matcher.find()) {
            val target = resolve(name, matcher.group(1));
            var replacement = matcher.group();
            val source = target == null || chain.contains(target) ? null : source(target);
            if (target != null && source != null) {
                chain.add(target);
                replacement = inline(target, new String(source, StandardCharsets.UTF_8), chain);
                chain.remove(target);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** The name of {@code path} imported by {@code name}; {@code null} when remote or not safe. */
    private static @Nullable String resolve(String name, String path) {
        if (path.contains(":") || path.startsWith("//")) return null;
        try {
            val target = URI.create(name).resolve(path).normalize().getPath();
            return target != null && safe(target) ? target : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The bytes of a regular file of the development directory, else of the classpath; {@code null} when none. */
    private byte @Nullable [] source(String name) throws IOException {
        if (directory != null) {
            val file = directory.resolve(name).normalize();
            if (file.startsWith(directory) && Files.exists(file)) {
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            }
        }
        return fromClasspath(name);
    }

    private byte @Nullable [] fromClasspath(String name) throws IOException {
        val resource = StaticFiles.class.getClassLoader().getResource(classpathRoot + "/" + name);
        if (resource == null || isDirectory(resource)) return null;
        try (InputStream input = resource.openStream()) {
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(Errors.get().sha256Unavailable().value(), e);
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
