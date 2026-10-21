package br.bundled.money.web.report;

import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.function.BiFunction;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /reports}: the list of the reports. {@code /reports/balance?month=2026-10},
 * {@code /reports/income?month=2026-10} and {@code /reports/evolution?month=2026-01} (the first of its twelve months,
 * January by default): the whole page, or only {@code #reports} when the month navigation asks.
 * {@code /reports/closing}: the closing date (INTENT F9).
 */
@NullMarked
public final class ReportRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get(ReportViews.LIST, MediaType.TEXT_HTML, String.class, this::list).getOrThrow();
        router.get(ReportViews.BALANCE, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, book.month(), ReportViews::balance)).getOrThrow();
        router.get(ReportViews.INCOME, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, book.month(), ReportViews::income)).getOrThrow();
        router.get(ReportViews.EVOLUTION, MediaType.TEXT_HTML, String.class,
                request -> monthly(request, book.month().withMonth(1), ReportViews::evolution)).getOrThrow();
        router.get(ReportViews.CLOSING, MediaType.TEXT_HTML, String.class, this::closing).getOrThrow();
        router.post(ReportViews.CLOSING, MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::close)
                .getOrThrow();
    }

    private HTTPResponse list(WebRequest request) {
        return Layout.page(request, Screen.REPORTS, ReportViews.list());
    }

    /** {@code report} of the month asked, {@code fallback} when none. */
    private HTTPResponse monthly(WebRequest request, YearMonth fallback, BiFunction<Book, YearMonth, Node> report) {
        val month = Ui.monthOf(request.parameter(Ledger.MONTH), fallback);
        val view = report.apply(book, month);
        return Ui.partial(request) ? ok(view.render()) : Layout.page(request, Screen.REPORTS, view);
    }

    /** The closing page, or only the card as it is (the cancel of a reopening). */
    private HTTPResponse closing(WebRequest request) {
        val card = ReportViews.closing(book, null, null);
        return Ui.partial(request) ? ok(card.render()) : Layout.page(request, Screen.REPORTS, ReportViews.closingPage(card));
    }

    /**
     * Sets the closing date; empty, nothing is closed. Moving it back (or clearing it) reopens transactions, so it asks
     * for the confirmation first. Without htmx: post/redirect/get when done; otherwise the card on its page ({@link #card}).
     */
    private HTTPResponse close(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val raw = form.value(ReportViews.THRESHOLD).strip();
        final LocalDate date;
        try {
            date = raw.isEmpty() ? null : LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return card(request, ReportViews.closing(book, null, "Data inválida."), true);
        }
        val current = book.threshold();
        val reopens = current != null && (date == null || date.isBefore(current));
        if (reopens && form.value(ReportViews.CONFIRM).isEmpty()) {
            return card(request, ReportViews.closing(book, raw, null), false);
        }
        val closed = book.close(date);
        if (closed instanceof Result.Failure<?, BusinessError>(var error)) {
            return card(request, ReportViews.closing(book, null, error.render()), true);
        }
        if (!HtmxRequest.of(request).isHtmx()) return WebResponse.redirect(ReportViews.CLOSING);
        return ok(ReportViews.closing(book, null, date == null ? "Período reaberto: nada está fechado."
                : (reopens ? "Período reaberto a partir de " : "Período fechado antes de ") + Ui.fullDay(date) + ".").render());
    }

    /** The closing card: alone for htmx; without, on its page ({@code refused}: 422). */
    private HTTPResponse card(WebRequest request, Node card, boolean refused) {
        return HtmxRequest.of(request).isHtmx() ? ok(card.render())
                : Layout.page(request, refused ? HTTPStatus.UNPROCESSABLE_CONTENT : HTTPStatus.OK, Screen.REPORTS,
                        ReportViews.closingPage(card));
    }
}
