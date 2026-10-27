package br.bundled.money.web;

import br.bundled.money.web.book.Book;
import br.bundled.money.web.seed.Seed;
import br.bundled.money.web.views.account.AccountRoutes;
import br.bundled.money.web.views.entry.EntryRoutes;
import br.bundled.money.web.views.home.HomeRoutes;
import br.bundled.money.web.views.installment.InstallmentRoutes;
import br.bundled.money.web.views.report.ReportRoutes;
import br.bundled.money.web.views.statement.StatementRoutes;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
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
 * {@link Book} the screens read, registered in the {@link Context}). Starting, it loads the seed ({@link Seed}). Open {@code http://localhost:9000}.
 */
public final class WebModule implements Lifecycle {

    /** Read from disk while developing, so the CSS reloads without a rebuild; from the classpath otherwise. */
    private static final Path STATIC_DIR = Path.of("src/main/java/br/bundled/money/web/static");

    private @Nullable WebServer server;

    @Override
    public Result<Void, BusinessError> initialize() {
        val seeded = Seed.load();
        if (seeded instanceof Result.Failure<?, BusinessError>(var error)) return Result.failure(error);
        Context.set(Book.class, Book::new);
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
                new HomeRoutes(),
                new AccountRoutes(),
                new StatementRoutes(),
                new InstallmentRoutes(),
                new EntryRoutes(),
                new ReportRoutes()
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
