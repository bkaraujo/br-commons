package br.bundled.judas._2_infrastructure.web;

import br.bundled.judas._2_infrastructure.web.views.home.HomeRoutes;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.lang.htmx.HtmxScript;
import br.commons.module.Lifecycle;
import br.commons.web.filter.RequestLoggingFilter;
import br.commons.web.filter.StaticFiles;
import br.commons.web.server.WebServer;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** The HTTP edge on port 9000: htmx, the static files under {@value #STATIC_URL} and the routes of each screen (ARCH-08). */
@NullMarked
public final class WebModule implements Lifecycle {

    static final int PORT = 9000;
    static final String STATIC_URL = "/static/judas/";
    private static final String STATIC_RESOURCES = "br/bundled/judas/_2_infrastructure/web/static";

    /** The sources on disk, when running from the project: CSS and scripts reload without a rebuild (UI-90). */
    private static final Path STATIC_SOURCES = Path.of("src/main/java", STATIC_RESOURCES);

    private @Nullable WebServer server;

    @Override
    public Result<Void, BusinessError> initialize() {
        val sources = Files.isDirectory(STATIC_SOURCES) ? STATIC_SOURCES : null;
        val started = new WebServer(PORT, List.of(
                new RequestLoggingFilter(),
                new HtmxScript(),
                new StaticFiles(STATIC_URL, STATIC_RESOURCES, sources, Duration.ZERO)));
        started.register(new HomeRoutes());
        server = started;
        return started.initialize();
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        if (server == null) return Result.success();
        return server.terminate();
    }
}
