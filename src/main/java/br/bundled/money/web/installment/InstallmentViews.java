package br.bundled.money.web.installment;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.book.Installment;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The installments screen: the home's accounts without the cards, each with a checkbox
 * ({@code #installments-accounts}), and the panel ({@code #installments-panel}) with the installments dated in the
 * month of the checked accounts and of every card, each with the account it is in, its {@code k/N} and its value;
 * the summary adds what is left to pay. A change of the checkboxes loads the panel with the month
 * ({@code #installments-month}); the month navigation carries the checked accounts. Read only: a transaction is
 * changed in the statement.
 */
@NullMarked
final class InstallmentViews {

    /** Present once the checkboxes were sent: absent, every account is checked. */
    static final String FILTERED = "filtered";
    /** The checkbox of an account: {@code account-<id>=1}. */
    static final String ACCOUNT = "account-";

    private static final String SCREEN = "installments";

    private InstallmentViews() {}

    /** The accounts and the panel of the month. */
    static Node screen(Book book, List<Account> accounts, List<Account> selected, List<Installment> installments,
                       YearMonth month) {
        return section(
                cls("statement"),
                accounts(accounts, selected),
                panel(book, selected, installments, month)
        );
    }

    /** A checkbox per account; a change sends the checked ones (and the month) and loads the panel. */
    private static Node accounts(List<Account> accounts, List<Account> selected) {
        return form(id(SCREEN + "-accounts"), cls("statement-accounts card"), ariaLabel(message("Contas")),
                method(FormMethod.GET), action(Screen.INSTALLMENTS.path()),
                hxGet(Screen.INSTALLMENTS.path()), hxTrigger("change"),
                hxInclude("#" + SCREEN + "-accounts, #" + SCREEN + "-month"),
                hxTarget("#" + SCREEN + "-panel"), hxSwap(HxSwap.OUTER_HTML), hxPushUrl(true),
                h3(text(message("Contas"))),
                input(type("hidden"), name(FILTERED), value("1")),
                ul(each(accounts, account -> li(label(cls("account-option"),
                        input(type("checkbox"), name(ACCOUNT + account.identity()), value("1"),
                                selected.contains(account) ? checked() : (Content) fragment()),
                        span(text(Text.data(account.name()))))))));
    }

    /** The month: navigation (with the checked accounts), summary and the installments. */
    static Node panel(Book book, List<Account> selected, List<Installment> installments, YearMonth month) {
        return div(
                id(SCREEN + "-panel"), cls("statement-panel installments-panel"),
                input(type("hidden"), id(SCREEN + "-month"), name(Ledger.MONTH), value(month.toString())),
                div(cls("ledger-head"),
                        div(p(cls("caption"), text(message("Parcelamentos"))),
                                h2(text(message(selected.isEmpty() ? "Nenhuma conta selecionada"
                                        : selected.size() + (selected.size() == 1 ? " conta" : " contas") + " e os cartões"))))),
                summary(installments),
                div(cls("ledger-scroll"), table(
                        cls("records ledger-table"),
                        thead(tr(th(text(message("Data"))), th(text(message("Conta"))), th(text(message("Descrição"))),
                                th(cls("num"), text(message("Parcela"))), th(cls("num"), text(message("Valor"))))),
                        tbody(
                                each(installments, installment -> line(book, installment)),
                                when(installments.isEmpty(), () -> tr(cls("empty"), td(colspan(5), text(message(
                                        selected.isEmpty() ? "Marque uma conta para ver as parcelas."
                                                : "Nenhuma parcela em " + Ui.month(month) + ".")))))))
                )
        );
    }

    /** How many, the month's value, how many end in it and what is left after it. */
    private static Node summary(List<Installment> installments) {
        val value = installments.stream().map(installment -> installment.line().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        val remaining = installments.stream().map(Installment::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        val ending = installments.stream().filter(installment -> installment.left() == 0).count();
        return dl(cls("summary"),
                div(dt(text(message("Parcelas"))), dd(text(Text.data(String.valueOf(installments.size()))))),
                div(dt(text(message("Valor do mês"))), dd(Ui.amount(value.abs(), false))),
                div(dt(text(message("Última parcela"))), dd(text(Text.data(String.valueOf(ending))))),
                div(cls("closing"), dt(text(message("Restante"))), dd(Ui.amount(remaining.abs(), false))));
    }

    /** A line dated after today is planned, shown apart; the account the installment is in; the last one is marked. */
    private static Node line(Book book, Installment installment) {
        val transaction = installment.line().transaction();
        val planned = transaction.date().isAfter(book.today());
        return tr(
                cls(planned ? "planned" : ""),
                td(time(datetime(transaction.date().toString()), text(Text.data(Ui.day(transaction.date())))),
                        when(planned, () -> small(cls("sub"), text(message("previsto"))))),
                td(cls("account"), text(Text.data(installment.account().name()))),
                td(strong(text(Text.data(transaction.description()))),
                        small(cls("sub"), text(Text.data(installment.line().counterpart())))),
                td(cls("num"), text(Text.data(installment.number() + "/" + installment.total())),
                        when(installment.left() == 0, () -> small(cls("sub"), text(message("última"))))),
                td(cls("num"), Ui.amount(installment.line().amount().abs(), false))
        );
    }
}
