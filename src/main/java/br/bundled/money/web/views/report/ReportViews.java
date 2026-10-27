package br.bundled.money.web.views.report;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.tools.Formatters;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static br.bundled.money.web.views.Ui.message;
import static br.commons.lang.Html5.*;

/**
 * The list of the reports, and each report on its own page: the balance sheet (Ativo = Passivo + PL + resultado
 * acumulado) and the DRE of the month, each group with its accounts one level down, the evolution of income and
 * expenses over twelve months. Everything that changes with the month is inside {@code #reports}.
 */
@NullMarked
final class ReportViews {

    static final String LIST = "/reports";
    static final String BALANCE = "/reports/balance";
    static final String INCOME = "/reports/income";
    static final String EVOLUTION = "/reports/evolution";

    /** A report of the list: where it is, and the keys of its name and of what it shows. */
    private record Report(String path, String name, String summary) {}

    private static final List<Report> REPORTS = List.of(
            new Report(BALANCE, "forms.report.balance_name", "forms.report.balance_summary"),
            new Report(INCOME, "forms.report.income_name", "forms.report.income_summary"),
            new Report(EVOLUTION, "forms.report.evolution_name", "forms.report.evolution_summary"));

    /** How many months the evolution shows, from the one picked. */
    private static final int SPAN = 12;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReportViews() {}

    /** The reports available, each a link to its page. */
    static Node list() {
        return section(
                id("reports"), cls("reports"),
                div(cls("ledger-head"), h2(text(message("forms.report.title")))),
                nav(cls("report-list card"), ariaLabel(message("forms.report.available")),
                        ul(each(REPORTS, report -> li(a(href(report.path()),
                                span(cls("report-name"), text(message(report.name()))),
                                span(cls("caption"), text(message(report.summary()))))))))
        );
    }

    static Node balance(Book book, YearMonth month) {
        return monthly(balanceSheet(book, month.atEndOfMonth()));
    }

    static Node income(Book book, YearMonth month) {
        return monthly(incomeStatement(book, month));
    }

    static Node evolution(Book book, YearMonth from) {
        return monthly(evolutionTable(book, from));
    }

    /** A report of a month: the way back to the list and the report; the month is navigated in the header. */
    private static Node monthly(Node report) {
        return section(
                id("reports"), cls("reports"),
                div(cls("ledger-head"), back()),
                report
        );
    }

    private static Node back() {
        return a(cls("back"), href(LIST), text(message("forms.report.back")));
    }

    // ---------- balance sheet

    private static Node balanceSheet(Book book, LocalDate day) {
        val asset = total(book, AccountType.ASSET, day);
        val liability = total(book, AccountType.LIABILITY, day);
        val equity = total(book, AccountType.EQUITY, day);
        val result = total(book, AccountType.INCOME, day).subtract(total(book, AccountType.EXPENSE, day));
        return article(
                cls("report card"),
                h3(text(message("forms.report.balance_name"))),
                p(cls("caption"), text(message("forms.report.as_of", Formatters.fullDay(day)))),
                table(cls("report-table"),
                        tbody(group(book, AccountType.ASSET, day)),
                        tbody(group(book, AccountType.LIABILITY, day)),
                        tbody(group(book, AccountType.EQUITY, day),
                                tr(cls("item"), td(text(message("forms.report.accumulated_result"))), td(cls("num"), Formatters.amount(result, false)))),
                        tbody(cls("totals-row"),
                                tr(th(text(message("forms.report.total_assets"))), td(cls("num"), Formatters.amount(asset, false))),
                                tr(th(text(message("forms.report.liabilities_equity_result"))),
                                        td(cls("num"), Formatters.amount(liability.add(equity).add(result), false)))))
        );
    }

    /**
     * The top-level account with its total, then every descendant indented, converted to the nature of the group
     * (a credit card inside the assets is negative), so the lines add up to the total.
     */
    private static Node group(Book book, AccountType type, LocalDate day) {
        return book.root(type).map(root -> {
            val subtree = book.subtree(root);
            return fragment(
                tr(cls("group"), th(text(Text.data(root.name()))), td(cls("num"), Formatters.amount(book.balance(root, day), false))),
                each(book.tree().stream().filter(account -> !account.equals(root) && subtree.contains(account))
                                .toList(),
                        child -> tr(cls("item depth-" + Math.min(book.depth(child), 3)),
                        td(a(href(Screen.STATEMENT.path() + "?account=" + child.identity()), text(Text.data(child.name())))),
                        td(cls("num"), book.children(child).isEmpty() || child.financial()
                                ? Formatters.amount(book.balanceAs(child, day, type), false) : fragment()))));
        }).orElseGet(Html::fragment);
    }

    private static BigDecimal total(Book book, AccountType type, LocalDate day) {
        return book.root(type).map(root -> book.balance(root, day)).orElse(BigDecimal.ZERO);
    }

    // ---------- income statement

    /** Income and expenses of the month, every account of the second level; each with a bar of its share of the total. */
    private static Node incomeStatement(Book book, YearMonth month) {
        val income = book.root(AccountType.INCOME).map(root -> book.flow(root, month)).orElse(BigDecimal.ZERO);
        val expense = book.root(AccountType.EXPENSE).map(root -> book.flow(root, month)).orElse(BigDecimal.ZERO);
        val result = income.subtract(expense);
        return article(
                cls("report card"),
                h3(text(message("forms.report.income_name"))),
                p(cls("caption"), text(Text.data(Formatters.month(month)))),
                table(cls("report-table"),
                        tbody(flows(book, AccountType.INCOME, month, income)),
                        tbody(flows(book, AccountType.EXPENSE, month, expense)),
                        tbody(cls("totals-row"),
                                tr(th(text(message(result.signum() < 0 ? "forms.report.deficit" : "forms.report.surplus"))),
                                        td(cls("num"), Formatters.amount(result, true))))),
                when(income.signum() > 0, () -> p(cls("caption"), text(message("forms.report.saving_rate",
                        result.multiply(HUNDRED).divide(income, 0, RoundingMode.HALF_UP)))))
        );
    }

    private static Node flows(Book book, AccountType type, YearMonth month, BigDecimal total) {
        return book.root(type).map(root -> fragment(
                tr(cls("group"), th(text(Text.data(root.name()))), td(cls("num"), Formatters.amount(total, false))),
                each(book.children(root), child -> share(book, child, month, total)))
        ).orElseGet(Html::fragment);
    }

    private static Node share(Book book, Account account, YearMonth month, BigDecimal total) {
        val flow = book.flow(account, month);
        return tr(cls("item"),
                td(a(href(Screen.STATEMENT.path() + "?account=" + account.identity() + "&" + Ledger.MONTH + "=" + month),
                                text(Text.data(account.name()))),
                        meter(min("0"), max(total.toPlainString()), value(flow.toPlainString()),
                                title(Text.data(Formatters.money(flow))))),
                td(cls("num"), Formatters.amount(flow, false)));
    }

    // ---------- evolution

    /**
     * Twelve months from {@code from}, income and expenses by account, every one of them, moved or not. Each value with
     * its share of the month's income, leading to the account's statement of that month; the total with the period's;
     * the average of the months up to the current one. Every parent opens and closes its subaccounts
     * ({@code static/money/outline.js}).
     */
    private static Node evolutionTable(Book book, YearMonth from) {
        val months = Stream.iterate(from, month -> month.plusMonths(1)).limit(SPAN).toList();
        val flows = months.stream().map(book::flows).toList();
        val revenue = book.root(AccountType.INCOME)
                .map(root -> flows.stream().map(flow -> flow.getOrDefault(root, BigDecimal.ZERO)).toList())
                .orElseGet(() -> Collections.nCopies(SPAN, BigDecimal.ZERO));
        val current = YearMonth.from(book.today());
        val elapsed = (int) months.stream().filter(month -> !month.isAfter(current)).count();
        val tree = book.tree();
        val rows = Stream.of(AccountType.INCOME, AccountType.EXPENSE).map(book::root).flatMap(Optional::stream)
                .flatMap(root -> tree.stream().filter(book.subtree(root)::contains)).toList();
        val parents = rows.stream().map(Account::parent).filter(Objects::nonNull).collect(Collectors.toSet());
        val evolution = new Evolution(months, flows, revenue, sum(revenue), elapsed);
        return article(
                cls("report card"),
                h3(text(message("forms.report.evolution_name"))),
                p(cls("caption"), text(message("forms.report.evolution_caption",
                        Formatters.month(from), Formatters.month(months.getLast()), Formatters.month(current)))),
                div(cls("scroller"),
                        table(cls("report-table evolution outline"),
                                thead(tr(th(text(message("forms.common.account"))),
                                        each(months, month -> th(cls("num"), text(Text.data(Formatters.shortMonth(month))))),
                                        th(cls("num"), text(message("forms.report.total"))), th(cls("num"), text(message("forms.report.average"))))),
                                tbody(each(rows, account -> evolutionRow(book, account, parents.contains(account), evolution)))))
        );
    }

    /**
     * What the rows of the evolution share: the months, the flows of each, the income of each and of the period, and
     * how many months (from the first) count for the average.
     */
    private record Evolution(List<YearMonth> months, List<Map<Account, BigDecimal>> flows, List<BigDecimal> revenue,
                             BigDecimal total, int elapsed) {}

    /**
     * The account with its months (each a link to its statement), total and average; a parent with the button that
     * opens it.
     */
    private static Node evolutionRow(Book book, Account account, boolean parent, Evolution evolution) {
        val depth = book.depth(account);
        val values = evolution.flows().stream().map(flow -> flow.getOrDefault(account, BigDecimal.ZERO)).toList();
        val total = sum(values);
        val elapsed = evolution.elapsed();
        val average = elapsed == 0 ? BigDecimal.ZERO
                : sum(values.subList(0, elapsed)).divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        val above = account.parent();
        return tr(
                cls("depth-" + Math.min(depth, 3) + (depth == 0 ? " group" : "")),
                data("row", account.identity().toString()),
                above == null ? fragment() : data("parent", above.identity().toString()),
                td(when(parent, () -> button(type(ButtonType.BUTTON), cls("toggle"), ariaExpanded(false),
                                ariaLabel(message("forms.report.toggle", account.name())),
                                span(cls("chevron"), ariaHidden(true)))),
                        text(Text.data(account.name()))),
                each(IntStream.range(0, SPAN).boxed().toList(), index -> td(cls("num"), values.get(index).signum() == 0
                        ? nothing()
                        : a(href(Screen.STATEMENT.path() + "?account=" + account.identity() + "&" + Ledger.MONTH + "="
                                        + evolution.months().get(index)),
                                share(values.get(index), evolution.revenue().get(index))))),
                td(cls("num total"), share(total, evolution.total())),
                td(cls("num"), average.signum() == 0 ? nothing() : Formatters.amount(average, false))
        );
    }

    /** The value and its share of {@code base} (the income of the same months); a dash when zero. */
    private static Node share(BigDecimal value, BigDecimal base) {
        if (value.signum() == 0) return nothing();
        return fragment(Formatters.amount(value, false), small(cls("share"), text(Text.data(base.signum() == 0 ? "—"
                : value.multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"))));
    }

    private static Node nothing() {
        return span(cls("caption"), text(Text.data("—")));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
