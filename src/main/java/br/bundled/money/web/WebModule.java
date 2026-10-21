package br.bundled.money.web;

import br.bundled.money.web.account.AccountRoutes;
import br.bundled.money.web.entry.EntryRoutes;
import br.bundled.money.web.home.HomeRoutes;
import br.bundled.money.business._1_application.ApplicationModule;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.seed.Seed;
import br.bundled.money.web.report.ReportRoutes;
import br.bundled.money.web.statement.StatementRoutes;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.lang.htmx.HtmxScript;
import br.commons.module.Lifecycle;
import br.commons.web.filter.RequestLoggingFilter;
import br.commons.web.filter.StaticFiles;
import br.commons.web.server.WebServer;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * The web server of br.money on port 9000: static files and the routes of each screen over the use cases (the
 * {@link Book} the screens read). Starting, it loads the seed ({@link Seed}). Open {@code http://localhost:9000}.
 */
public final class WebModule implements Lifecycle {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/java/br/bundled/money/web/static");

    private final ApplicationModule app;
    private @Nullable WebServer server;

    public WebModule(ApplicationModule app) {
        this.app = app;
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        val seeded = Seed.load(app);
        if (seeded instanceof Result.Failure<?, BusinessError>(var error)) return Result.failure(error);
        val book = new Book(app);
        val developing = Files.isDirectory(STATIC_DIR);
        server = new WebServer(
                9000,
                List.of(
                        new RequestLoggingFilter(),
                        new HtmxScript(),
                        new StaticFiles("/static/money/", "br/bundled/money/web/static", developing ? STATIC_DIR : null, Duration.ZERO)
                )
        );

        server.register(
                new HomeRoutes(book),
                new AccountRoutes(book),
                new StatementRoutes(book),
                new EntryRoutes(book),
                new ReportRoutes(book)
        );

        return server.initialize();
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        if (server != null) {
            return server.terminate();
        }

        return Result.success();
    }
}
