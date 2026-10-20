package br.bundled.money.web.home;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;
import java.util.Set;
import java.util.stream.IntStream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The home: the financial accounts on the left, as the statement's list, each with a checkbox, and the income
 * against the expenses of the last 12 months of the checked ones in the middle ({@code #home-chart}). Checking
 * reloads only the chart. Only financial accounts: categories and technical accounts are in the chart of accounts
 * (INTENT §7, "visões separadas"). The chart is the table of the months; {@code static/money/chart.js} draws its
 * lines from the {@code data-*} of each row.
 */
@NullMarked
final class HomeViews {

    /** Sent with the checkboxes: without it, every account is checked (the first visit). */
    static final String FILTERED = "filtered";
    static final String ACCOUNT = "account-";

    private static final int MONTHS = 12;

    private HomeViews() {}

    static Node screen(Book book, Set<Integer> selected) {
        return section(
                id("home"), cls("home statement"),
                accounts(book, selected),
                chart(book, selected),
                a(cls("fab"), href(Screen.ENTRY.path()), hxBoost(true), ariaLabel(message("Novo lançamento")),
                        text(Text.data("+")))
        );
    }

    /** Without htmx the form goes to the home itself, with the checked accounts. */
    private static Node accounts(Book book, Set<Integer> selected) {
        return form(id("home-accounts"), cls("statement-accounts card"), ariaLabel(message("Contas")),
                method(FormMethod.GET), action(Screen.HOME.path()),
                hxGet("/home/chart"), hxTrigger("change"), hxTarget("#home-chart"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(message("Minhas contas"))),
                input(type("hidden"), name(FILTERED), value("1")),
                ul(each(book.financial(), account -> li(option(account, selected.contains(account.id()))))));
    }

    /** A checkbox with the name. */
    private static Node option(MockAccount account, boolean checked) {
        return label(cls("account-option"),
                input(type("checkbox"), name(ACCOUNT + account.id()), value("1"), checked ? checked() : (Content) fragment()),
                span(text(Text.data(account.name()))));
    }

    /** Income and expenses of the last 12 months of the checked accounts, the current one last. */
    static Node chart(Book book, Set<Integer> selected) {
        val current = YearMonth.from(book.today());
        val months = IntStream.range(0, MONTHS).mapToObj(back -> current.minusMonths(MONTHS - 1 - back)).toList();
        return div(
                id("home-chart"), cls("home-chart card"),
                h2(text(message("Receitas x Despesas"))),
                p(cls("caption"), text(message(selected.isEmpty() ? "Nenhuma conta selecionada."
                        : "Últimos 12 meses · " + selected.size() + (selected.size() == 1 ? " conta" : " contas")))),
                div(cls("line-chart"), ariaHidden(true)),
                table(
                        cls("records chart-data"),
                        thead(tr(th(text(message("Mês"))), th(cls("num"), text(message("Receitas"))),
                                th(cls("num"), text(message("Despesas"))))),
                        tbody(each(months.reversed(), month -> row(book, selected, month)))
                )
        );
    }

    /** A month, newest first; the chart reads its {@code data-*} and sorts them by time. */
    private static Node row(Book book, Set<Integer> selected, YearMonth month) {
        val income = book.categoryFlow(selected, month, AccountType.INCOME);
        val expense = book.categoryFlow(selected, month, AccountType.EXPENSE);
        return tr(data("label", Ui.shortMonth(month)), data("income", income.toPlainString()),
                data("expense", expense.toPlainString()),
                td(text(Text.data(Ui.month(month)))),
                td(cls("num"), Ui.amount(income, false)),
                td(cls("num"), Ui.amount(expense, false)));
    }
}
