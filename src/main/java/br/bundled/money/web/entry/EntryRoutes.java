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
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /entries/new}: the quick entry, as a dialog over the screen when htmx asks (the menu), else its page.
 * {@code GET /entries/editor}: the dialog emptied. {@code POST /entries}: from the dialog ({@link EntryViews#MODAL}),
 * valid, the dialog closes and the content behind reloads itself ({@link Layout#CHANGED}), invalid, the form comes
 * back with its errors; from the page, valid, the form ready for the next one with what was saved. Without htmx:
 * post/redirect/get to the home.
 */
@NullMarked
public final class EntryRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get("/entries/new", MediaType.TEXT_HTML, String.class, this::blank).getOrThrow();
        router.get(Layout.ENTRY_EDITOR, MediaType.TEXT_HTML, String.class, _ -> ok("")).getOrThrow();
        router.post("/entries", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
    }

    private HTTPResponse blank(WebRequest request) {
        val values = EntryFields.blank(book.today(), null);
        return Ui.partial(request) ? ok(view(true, values, List.of(), null).render())
                : Layout.page(request, Screen.ENTRY, view(false, values, List.of(), null));
    }

    /** The dialog (a card to open over a screen) or the page, with nothing saved yet. */
    private Node view(boolean modal, Map<String, String> values, List<Violation> errors, @Nullable String refused) {
        return modal ? EntryViews.modal(book, values, errors, refused)
                : EntryViews.screen(book, values, errors, null, refused);
    }

    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        val modal = htmx && !form.value(EntryViews.MODAL).isEmpty();
        return switch (EntryFields.read(form::value, book)) {
            case Result.Success<QuickEntry, List<Violation>>(var value) -> {
                val entry = Objects.requireNonNull(value);
                val saved = book.quick(entry.date(), entry.description(), entry.account(), entry.category(), entry.amount());
                if (saved instanceof Result.Failure<Transaction, BusinessError>(var error)) {
                    val view = view(modal, EntryFields.values(form::value), List.of(), error.render());
                    yield htmx ? ok(view.render()) : Layout.page(request, Screen.ENTRY, view);
                }
                // on the body: the dialog may have closed before the answer, taking the form that asked out of the DOM
                if (modal) yield HtmxResponse.of(ok("")).trigger(Map.of(Layout.CHANGED, Map.of("target", "body"))).response();
                yield htmx ? ok(EntryViews.screen(book, EntryFields.blank(entry.date(), entry.account()), List.of(),
                        saved.getOrThrow(), null).render()) : WebResponse.redirect(Screen.HOME.path());
            }
            case Result.Failure<QuickEntry, List<Violation>>(var errors) -> {
                val view = view(modal, EntryFields.values(form::value), errors, null);
                yield htmx ? ok(view.render()) : Layout.page(request, Screen.ENTRY, view);
            }
        };
    }
}
