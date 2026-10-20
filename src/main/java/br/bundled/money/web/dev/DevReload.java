package br.bundled.money.web.dev;

import br.bundled.money.web.ui.Ui;
import br.commons.protocol.http.MediaType;
import br.commons.web.router.Router;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code GET /dev/reload}: a token that changes when the server restarts or a watched file changes (the static
 * files and the compiled classes, which a hotswap rewrites). {@code static/money/dev.js} asks for it every second
 * and, when it changes, swaps the body by htmx: the page looks hot-reloaded. Registered only while developing.
 */
@NullMarked
public final class DevReload implements WebResource {

    private final String boot = Long.toString(System.currentTimeMillis(), 36);
    private final List<Path> watched;

    public DevReload(Path staticDir) {
        val paths = new ArrayList<>(List.of(staticDir));
        classes().ifPresent(paths::add);
        this.watched = List.copyOf(paths);
    }

    @Override
    public void routes(Router router) {
        router.get("/dev/reload", MediaType.TEXT_HTML, String.class, request -> Ui.ok(token())).getOrThrow();
    }

    private String token() {
        return boot + "-" + Long.toString(watched.stream().mapToLong(DevReload::lastModified).max().orElse(0), 36);
    }

    /** The newest modification under {@code root}; a file removed while walking is skipped. */
    private static long lastModified(Path root) {
        try (val files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.getLastModifiedTime(file).toMillis();
                } catch (IOException e) {
                    return 0;
                }
            }).max().orElse(0);
        } catch (IOException | UncheckedIOException e) {
            return 0;
        }
    }

    /** The directory the classes are loaded from, when it is one (the IDE or Maven output, not a jar). */
    private static Optional<Path> classes() {
        val source = DevReload.class.getProtectionDomain().getCodeSource();
        if (source == null) return Optional.empty();
        try {
            val location = Path.of(source.getLocation().toURI());
            return Files.isDirectory(location) ? Optional.of(location) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
