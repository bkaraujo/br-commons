package br.bundled.money.web.views.report;

import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.views.Layout;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Ui;
import br.bundled.money.web.views.account.AccountRoutes;
import br.bundled.money.web.views.tools.Validators;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.lang.html.core.Node;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static br.bundled.money.web.views.Ui.ok;

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
    public List<Result<Route, String>> routes() {
        return List.of(
                Route.get(ReportViews.LIST, MediaType.TEXT_HTML, String.class, true, this::list),
                Route.get(ReportViews.BALANCE, MediaType.TEXT_HTML, String.class,
                        true, request -> monthly(request, book::month, ReportViews::balance)),
                Route.get(ReportViews.INCOME, MediaType.TEXT_HTML, String.class,
                        true, request -> monthly(request, book::month, ReportViews::income)),
                Route.get(ReportViews.EVOLUTION, MediaType.TEXT_HTML, String.class,
                        true, request -> monthly(request, () -> book.month().withMonth(1), ReportViews::evolution)),
                Route.get("/reports/closing", MediaType.TEXT_HTML, String.class, true, _ -> WebResponse.redirect(AccountRoutes.CLOSING)));
    }

    private HTTPResponse list(WebRequest request) {
        return Layout.page(request, Screen.REPORTS, ReportViews.list());
    }

    /** {@code report} of the month asked, {@code fallback} when none. */
    private HTTPResponse monthly(WebRequest request, Supplier<YearMonth> fallback, BiFunction<Book, YearMonth, Node> report) {
        val month = Validators.monthOf(request.parameter(Ledger.MONTH), fallback);
        val view = report.apply(book, month);
        return Ui.partial(request) ? ok(view.render())
                : Layout.page(request, Screen.REPORTS, Ledger.monthNav(book, month), view);
    }
}
