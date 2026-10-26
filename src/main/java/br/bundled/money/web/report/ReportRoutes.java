package br.bundled.money.web.report;

import br.bundled.money.web.account.AccountRoutes;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.cdi.Context;
import br.commons.lang.html.core.Node;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /reports}: the list of the reports. {@code /reports/balance?month=2026-10},
 * {@code /reports/income?month=2026-10} and {@code /reports/evolution?month=2026-01} (the first of its twelve months,
 * January by default): the whole page, or only {@code #reports} when the month navigation asks.
 * {@code /reports/closing}, where the closing date used to be: redirects to it in the chart of accounts.
 */
@NullMarked
public final class ReportRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get(ReportViews.LIST, MediaType.TEXT_HTML, String.class, this::list).getOrThrow();
        router.get(ReportViews.BALANCE, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, book::month, ReportViews::balance)).getOrThrow();
        router.get(ReportViews.INCOME, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, book::month, ReportViews::income)).getOrThrow();
        router.get(ReportViews.EVOLUTION, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, () -> book.month().withMonth(1), ReportViews::evolution)).getOrThrow();
        router.get("/reports/closing", MediaType.TEXT_HTML, String.class, _ -> WebResponse.redirect(AccountRoutes.CLOSING))
                .getOrThrow();
    }

    private HTTPResponse list(WebRequest request) {
        return Layout.page(request, Screen.REPORTS, ReportViews.list());
    }

    /** {@code report} of the month asked, {@code fallback} when none. */
    private HTTPResponse monthly(WebRequest request, Supplier<YearMonth> fallback, BiFunction<Book, YearMonth, Node> report) {
        val month = Ui.monthOf(request.parameter(Ledger.MONTH), fallback);
        val view = report.apply(book, month);
        return Ui.partial(request) ? ok(view.render())
                : Layout.page(request, Screen.REPORTS, Ledger.monthNav(book, month), view);
    }
}
