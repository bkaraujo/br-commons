package br.bundled.money.web.report;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.htmx.HtmxRequest;
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
import java.time.format.DateTimeParseException;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /reports?month=2026-10}: the whole page, or only {@code #reports} when the month navigation asks.
 * {@code /reports/closing}: the closing date (INTENT F9).
 */
@NullMarked
public final class ReportRoutes implements WebResource {

    private final Book book;

    public ReportRoutes(Book book) {
        this.book = book;
    }

    @Override
    public void routes(Router router) {
        router.get("/reports", MediaType.TEXT_HTML, String.class, this::reports).getOrThrow();
        router.get(ReportViews.CLOSING, MediaType.TEXT_HTML, String.class, this::closing).getOrThrow();
        router.post(ReportViews.CLOSING, MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::close)
                .getOrThrow();
    }

    private HTTPResponse reports(WebRequest request) {
        val month = Ui.monthOf(request.parameter("month"), book.month());
        val view = ReportViews.screen(book, month);
        return Ui.partial(request) ? ok(view.render()) : Layout.page(request, Screen.REPORTS, view);
    }

    /** The closing card as it is (the cancel of a reopening). */
    private HTTPResponse closing(WebRequest request) {
        return Ui.partial(request) ? ok(ReportViews.closing(book, null, null).render()) : WebResponse.redirect("/reports");
    }

    /**
     * Sets the closing date; empty, nothing is closed. Moving it back (or clearing it) reopens transactions, so it asks
     * for the confirmation first. Without htmx: post/redirect/get.
     */
    private HTTPResponse close(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val raw = form.value(ReportViews.THRESHOLD).strip();
        final LocalDate date;
        try {
            date = raw.isEmpty() ? null : LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return ok(ReportViews.closing(book, null, "Data inválida.").render());
        }
        val current = book.threshold();
        val reopens = current != null && (date == null || date.isBefore(current));
        if (reopens && form.value(ReportViews.CONFIRM).isEmpty()) {
            return ok(ReportViews.closing(book, raw, null).render());
        }
        book.close(date);
        if (!HtmxRequest.of(request).isHtmx()) return WebResponse.redirect("/reports");
        return ok(ReportViews.closing(book, null, date == null ? "Período reaberto: nada está fechado."
                : (reopens ? "Período reaberto a partir de " : "Período fechado antes de ") + Ui.fullDay(date) + ".").render());
    }
}
