package br.bundled.money.web.views.home;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.views.Layout;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Ui;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Route;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static br.bundled.money.web.views.Ui.ok;

/**
 * {@code GET /}: the whole page, or only the chart ({@code #home-chart}) of the accounts checked ({@code account-<id>})
 * when the checkboxes ask.
 */
@NullMarked
public final class HomeRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public List<Result<Route, String>> routes() {
        return List.of(
                Route.get("/", MediaType.TEXT_HTML, String.class, true, this::home));
    }

    private HTTPResponse home(WebRequest request) {
        val selected = selected(request);
        return Ui.partial(request) ? ok(HomeViews.chart(book, selected).render())
                : Layout.page(request, Screen.HOME, HomeViews.screen(book, selected));
    }

    /** The checked financial accounts; all of them before the first change. */
    private Set<Account> selected(WebRequest request) {
        val filtered = request.parameter(HomeViews.FILTERED) != null;
        return book.moneyAccounts().stream()
                .filter(account -> !filtered || request.parameter(HomeViews.ACCOUNT + account.identity()) != null)
                .collect(Collectors.toSet());
    }
}
