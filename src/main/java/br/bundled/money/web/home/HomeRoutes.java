package br.bundled.money.web.home;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.cdi.Context;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Set;
import java.util.stream.Collectors;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /}: the whole page, or only {@code #home} when htmx asks for it. {@code GET /home/chart}: only the
 * chart, of the accounts checked ({@code account-<id>}).
 */
@NullMarked
public final class HomeRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get("/", MediaType.TEXT_HTML, String.class, this::home).getOrThrow();
        router.get("/home/chart", MediaType.TEXT_HTML, String.class,
                request -> ok(HomeViews.chart(book, selected(request)).render())).getOrThrow();
    }

    private HTTPResponse home(WebRequest request) {
        val view = HomeViews.screen(book, selected(request));
        return Ui.partial(request) ? ok(view.render()) : Layout.page(request, Screen.HOME, view);
    }

    /** The checked financial accounts; all of them before the first change. */
    private Set<Account> selected(WebRequest request) {
        val filtered = request.parameter(HomeViews.FILTERED) != null;
        return book.moneyAccounts().stream()
                .filter(account -> !filtered || request.parameter(HomeViews.ACCOUNT + account.identity()) != null)
                .collect(Collectors.toSet());
    }
}
