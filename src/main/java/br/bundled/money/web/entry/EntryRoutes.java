package br.bundled.money.web.entry;

import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.lang.html.form.Violation;
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

import java.util.List;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /entries/new}: the quick entry. {@code POST /entries}: valid, the form ready for the next one with
 * what was saved; invalid, the form with its errors. Without htmx: post/redirect/get to the home.
 */
@NullMarked
public final class EntryRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get("/entries/new", MediaType.TEXT_HTML, String.class, this::blank).getOrThrow();
        router.post("/entries", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
    }

    private HTTPResponse blank(WebRequest request) {
        val view = EntryViews.screen(book, EntryFields.blank(book.today(), null), List.of(), null, null);
        return Ui.partial(request) ? ok(view.render()) : Layout.page(request, Screen.ENTRY, view);
    }

    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (EntryFields.read(form::value, book)) {
            case Result.Success<QuickEntry, List<Violation>>(var entry) -> {
                val saved = book.quick(entry.date(), entry.description(), entry.account(), entry.category(), entry.amount());
                if (saved instanceof Result.Failure<Transaction, BusinessError>(var error)) {
                    val view = EntryViews.screen(book, EntryFields.values(form::value), List.of(), null, error.render());
                    yield htmx ? ok(view.render()) : Layout.page(request, Screen.ENTRY, view);
                }
                yield htmx ? ok(EntryViews.screen(book, EntryFields.blank(entry.date(), entry.account()), List.of(),
                        saved.getOrThrow(), null).render()) : WebResponse.redirect(Screen.HOME.path());
            }
            case Result.Failure<QuickEntry, List<Violation>>(var errors) -> {
                val view = EntryViews.screen(book, EntryFields.values(form::value), errors, null, null);
                yield htmx ? ok(view.render()) : Layout.page(request, Screen.ENTRY, view);
            }
        };
    }
}
