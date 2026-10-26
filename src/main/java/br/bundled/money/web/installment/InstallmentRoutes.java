package br.bundled.money.web.installment;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Ledger;
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

import java.util.ArrayList;
import java.util.List;

import static br.bundled.money.web.ui.Ui.ok;

/**
 * {@code GET /installments?filtered=1&account-<id>=1&month=2026-10}: the whole page, or only
 * {@code #installments-panel} when the checkboxes or the month navigation ask ({@link Ui#monthOnly}). The accounts are
 * the home's, without the cards; the installments are those of the checked accounts and of every card, none while no
 * account is checked.
 */
@NullMarked
public final class InstallmentRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get(Screen.INSTALLMENTS.path(), MediaType.TEXT_HTML, String.class, this::installments).getOrThrow();
    }

    private HTTPResponse installments(WebRequest request) {
        val month = Ui.monthOf(request.parameter(Ledger.MONTH), book::month);
        val accounts = book.moneyAccounts().stream().filter(account -> !Book.card(account)).toList();
        val selected = selected(request, accounts);
        val scope = new ArrayList<Account>();
        if (!selected.isEmpty()) {
            book.moneyAccounts().stream().filter(Book::card).forEach(scope::add);
            scope.addAll(selected);
        }
        val installments = book.installments(scope, month);
        if (Ui.partial(request) || Ui.monthOnly(request)) {
            val panel = InstallmentViews.panel(book, selected, installments, month);
            return Ui.partial(request) ? ok(panel.render())
                    : Layout.region(request, Ledger.monthNav(book, month), "#" + InstallmentViews.PANEL, panel);
        }
        return Layout.page(request, Screen.INSTALLMENTS, Ledger.monthNav(book, month),
                InstallmentViews.screen(book, accounts, selected, installments, month));
    }

    /** The checked accounts; all of them before the first change. */
    private static List<Account> selected(WebRequest request, List<Account> accounts) {
        val filtered = request.parameter(InstallmentViews.FILTERED) != null;
        return accounts.stream()
                .filter(account -> !filtered || request.parameter(InstallmentViews.ACCOUNT + account.identity()) != null)
                .toList();
    }
}
