package br.bundled.money.web.report;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;

import static br.bundled.money.web.ui.Ui.ok;

/** {@code GET /reports?month=2026-10}: the whole page, or only {@code #reports} when the month navigation asks. */
@NullMarked
public final class ReportRoutes implements WebResource {

    private final Book book;

    public ReportRoutes(Book book) {
        this.book = book;
    }

    @Override
    public void routes(Router router) {
        router.get("/reports", MediaType.TEXT_HTML, String.class, this::reports).getOrThrow();
    }

    private HTTPResponse reports(WebRequest request) {
        val month = Ui.monthOf(request.parameter("month"), YearMonth.from(book.today()));
        val view = ReportViews.screen(book, month);
        return (Ui.partial(request) ? ok(view.render()) : Layout.page(request, Screen.REPORTS, view)).header("Vary", "HX-Request, HX-Boosted");
    }
}
