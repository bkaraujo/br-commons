package br.bundled.money.web.account;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.LedgerLine;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/** The ledger of one account; the chart of accounts and its register are in {@link RegisterViews}. */
@NullMarked
final class AccountViews {

    private AccountViews() {}

    static Node missing() {
        return section(cls("registry"), h2(text(message("Conta não encontrada"))),
                p(a(href(Screen.ACCOUNTS.path()), text(message("Voltar ao plano de contas")))));
    }

    // ---------- ledger

    /**
     * The month of an account: navigation, the summary (opening, in, out, closing) and the lines with the running
     * balance. Everything that changes with the month is inside {@code #ledger}.
     */
    static Node ledger(Book book, MockAccount account, YearMonth month) {
        val lines = book.ledger(account.id(), month);
        val back = account.financial() ? Screen.HOME : Screen.ACCOUNTS;
        return section(
                id("ledger"), cls("ledger"),
                a(cls("back"), href(back.path()), hxBoost(true), text(message("← " + back.label()))),
                div(cls("ledger-head"),
                        div(p(cls("caption"), text(message(Ui.typeLabel(account.type())))),
                                h2(text(Text.data(book.path(account))))),
                        Ledger.monthNav(month, YearMonth.from(book.today()),
                                shown -> "/accounts/" + account.id() + "?month=" + shown, "#ledger")),
                Ledger.summary(book, account, month, lines),
                table(
                        cls("records ledger-table"),
                        Ledger.columns(),
                        tbody(
                                each(lines, AccountViews::line),
                                when(lines.isEmpty(), () -> tr(cls("empty"),
                                        td(colspan(4), text(message("Nenhum lançamento em " + Ui.month(month) + "."))))))
                )
        );
    }

    private static Node line(LedgerLine line) {
        val transaction = line.transaction();
        return tr(
                td(time(datetime(transaction.date().toString()), text(Text.data(Ui.day(transaction.date()))))),
                td(strong(text(Text.data(transaction.description()))),
                        small(cls("sub"), text(Text.data(line.counterpart())))),
                td(cls("num"), Ui.amount(line.amount(), true)),
                td(cls("num hide-narrow"), Ui.amount(line.balance(), false))
        );
    }
}
