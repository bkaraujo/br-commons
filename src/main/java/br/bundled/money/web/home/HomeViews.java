package br.bundled.money.web.home;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The home: the financial accounts on the left, as the statement's list, each with a checkbox, and the income
 * against the expenses of the last 12 months of the checked ones in the middle ({@code #home-chart}). Checking
 * reloads only the chart and keeps the checked accounts in the URL. Only financial accounts: categories and technical
 * accounts are in the chart of accounts (INTENT §7, "visões separadas"). The chart is the table of the months;
 * {@code static/money/chart.js} draws its lines from the {@code data-*} of each row.
 */
@NullMarked
final class HomeViews {

    /** Sent with the checkboxes: without it, every account is checked (the first visit). */
    static final String FILTERED = "filtered";
    static final String ACCOUNT = "account-";

    private static final int MONTHS = 12;

    private HomeViews() {}

    static Node screen(Book book, Set<Account> selected) {
        return section(
                id("home"), cls("home statement"),
                accounts(book, selected),
                chart(book, selected)
        );
    }

    /**
     * The accounts and the credit cards in separate cards, only to show they are of different kinds; an empty one
     * is left out. One form around both: a change sends every checked account (and {@link #FILTERED} once) to the
     * home, loads the chart and pushes the URL; without htmx the form goes to the home itself.
     */
    private static Node accounts(Book book, Set<Account> selected) {
        return form(cls("home-accounts"), ariaLabel(message("forms.common.accounts")), method(FormMethod.GET),
                action(Screen.HOME.path()), hxGet(Screen.HOME.path()), hxTrigger("change"), hxTarget("#home-chart"),
                hxSwap(HxSwap.OUTER_HTML), hxPushUrl(true),
                input(type("hidden"), name(FILTERED), value("1")),
                group("forms.common.accounts", book.bankAccounts(), selected),
                group("forms.common.cards", book.cards(), selected));
    }

    /** A titled card of checkable accounts. */
    private static Node group(String title, List<Account> accounts, Set<Account> selected) {
        return accounts.isEmpty() ? fragment() : div(cls("statement-accounts card"), role("group"),
                ariaLabel(message(title)),
                h3(text(message(title))),
                ul(each(accounts, account -> li(option(account, selected.contains(account))))));
    }

    /** A checkbox with the name. */
    private static Node option(Account account, boolean checked) {
        return label(cls("account-option"),
                input(type("checkbox"), name(ACCOUNT + account.identity()), value("1"), checked ? checked() : fragment()),
                span(text(Text.data(account.name()))));
    }

    /** Income and expenses of the last 12 months of the checked accounts, the current one last. */
    static Node chart(Book book, Set<Account> selected) {
        val current = book.month();
        val months = IntStream.range(0, MONTHS).mapToObj(back -> current.minusMonths(MONTHS - 1 - back)).toList();
        return div(
                id("home-chart"), cls("home-chart card"),
                h2(text(message("forms.home.chart_title"))),
                p(cls("caption"), text(selected.isEmpty() ? message("forms.home.none_selected")
                        : message(selected.size() == 1 ? "forms.home.last_months_one" : "forms.home.last_months", selected.size()))),
                div(cls("line-chart"), ariaHidden(true)),
                table(
                        cls("records chart-data"),
                        thead(tr(th(text(message("forms.common.month"))), th(cls("num"), text(message("forms.home.income"))),
                                th(cls("num"), text(message("forms.home.expenses"))))),
                        tbody(each(months.reversed(), month -> row(book, selected, month)))
                )
        );
    }

    /** A month, newest first; the chart reads its {@code data-*} and sorts them by time. */
    private static Node row(Book book, Set<Account> selected, YearMonth month) {
        val income = book.categoryFlow(selected, month, AccountType.INCOME);
        val expense = book.categoryFlow(selected, month, AccountType.EXPENSE);
        return tr(data("label", Ui.shortMonth(month)), data("income", income.toPlainString()),
                data("expense", expense.toPlainString()),
                td(text(Text.data(Ui.month(month)))),
                td(cls("num"), Ui.amount(income, false)),
                td(cls("num"), Ui.amount(expense, false)));
    }
}
