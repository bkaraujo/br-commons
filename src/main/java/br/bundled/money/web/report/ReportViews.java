package br.bundled.money.web.report;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The list of the reports, and each report on its own page: the balance sheet (Ativo = Passivo + PL + resultado
 * acumulado) and the DRE of the month, each group with its accounts one level down, the evolution of income and
 * expenses over twelve months, and the closing date. Everything that changes with the month is inside
 * {@code #reports}.
 */
@NullMarked
final class ReportViews {

    static final String LIST = "/reports";
    static final String BALANCE = "/reports/balance";
    static final String INCOME = "/reports/income";
    static final String EVOLUTION = "/reports/evolution";

    /** The closing form (INTENT F9): where it posts and its fields. */
    static final String CLOSING = "/reports/closing";
    static final String THRESHOLD = "threshold";
    static final String CONFIRM = "confirm";

    /** A report of the list: where it is, its name and what it shows. */
    private record Report(String path, String name, String summary) {}

    private static final List<Report> REPORTS = List.of(
            new Report(BALANCE, "Balanço patrimonial", "Ativo, passivo e patrimônio líquido no fim do mês"),
            new Report(INCOME, "Demonstração do resultado", "Receitas e despesas do mês, com o superávit ou déficit"),
            new Report(EVOLUTION, "Evolução de gastos", "Receitas e despesas por conta em doze meses, com o % da receita"),
            new Report(CLOSING, "Fechamento de período", "Data-limite antes da qual os lançamentos não mudam"));

    /** How many months the evolution shows, from the one picked. */
    private static final int SPAN = 12;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReportViews() {}

    /** The reports available, each a link to its page. */
    static Node list() {
        return section(
                id("reports"), cls("reports"),
                div(cls("ledger-head"), h2(text(message("Relatórios")))),
                nav(cls("report-list card"), ariaLabel(message("Relatórios disponíveis")),
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

    /** The closing card ({@link #closing}) on its own page; the card swaps itself, so there is no month to navigate. */
    static Node closingPage(Node card) {
        return section(id("reports"), cls("reports"), div(cls("ledger-head"), back()), card);
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
        return a(cls("back"), href(LIST), text(message("← Relatórios")));
    }

    // ---------- closing (INTENT F9)

    /**
     * The closing date: every transaction before it is immutable. {@code reopening}: the date asked, earlier than the
     * current one (or none), waiting for the confirmation; moving it back is an explicit act. {@code notice}: what the
     * last change did.
     */
    static Node closing(Book book, @Nullable String reopening, @Nullable String notice) {
        val threshold = book.threshold();
        val shown = reopening != null ? reopening : threshold == null ? "" : threshold.toString();
        return article(
                id("closing"), cls("report card closing"),
                h3(text(message("Fechamento de período"))),
                p(cls("caption"), text(message(threshold == null ? "Nenhum período fechado."
                        : "Lançamentos antes de " + Ui.fullDay(threshold) + " estão fechados: não podem ser criados, "
                        + "editados nem excluídos."))),
                notice == null ? fragment() : p(cls("notice"), role("status"), text(message(notice))),
                form(
                        cls("closing-form"), method(FormMethod.POST), action(CLOSING),
                        hxPost(CLOSING), hxTarget("#closing"), hxSwap(HxSwap.OUTER_HTML),
                        label(text(message("Data-limite")),
                                reopening == null ? input(type("date"), name(THRESHOLD), value(shown))
                                        : input(type("date"), name(THRESHOLD), value(shown), readonly())),
                        reopening == null
                                ? div(cls("buttons"), button(type(ButtonType.SUBMIT), text(message("Salvar"))))
                                : fragment(
                                        p(cls("error"), role("alert"), text(message(
                                                "Recuar a data-limite reabre os lançamentos do período para alteração."))),
                                        input(type("hidden"), name(CONFIRM), value("1")),
                                        div(cls("buttons"),
                                                button(type(ButtonType.SUBMIT), cls("danger"), text(message("Confirmar reabertura"))),
                                                button(type(ButtonType.BUTTON), cls("link"), hxGet(CLOSING), hxTarget("#closing"),
                                                        hxSwap(HxSwap.OUTER_HTML), text(message("Cancelar")))))
                )
        );
    }


    // ---------- balance sheet

    private static Node balanceSheet(Book book, LocalDate day) {
        val asset = total(book, AccountType.ASSET, day);
        val liability = total(book, AccountType.LIABILITY, day);
        val equity = total(book, AccountType.EQUITY, day);
        val result = total(book, AccountType.INCOME, day).subtract(total(book, AccountType.EXPENSE, day));
        return article(
                cls("report card"),
                h3(text(message("Balanço patrimonial"))),
                p(cls("caption"), text(message("em " + Ui.fullDay(day)))),
                table(cls("report-table"),
                        tbody(group(book, AccountType.ASSET, day)),
                        tbody(group(book, AccountType.LIABILITY, day)),
                        tbody(group(book, AccountType.EQUITY, day),
                                tr(cls("item"), td(text(message("Resultado acumulado"))), td(cls("num"), Ui.amount(result, false)))),
                        tbody(cls("totals-row"),
                                tr(th(text(message("Total do ativo"))), td(cls("num"), Ui.amount(asset, false))),
                                tr(th(text(message("Passivo + PL + resultado"))),
                                        td(cls("num"), Ui.amount(liability.add(equity).add(result), false)))))
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
                tr(cls("group"), th(text(Text.data(root.name()))), td(cls("num"), Ui.amount(book.balance(root, day), false))),
                each(book.tree().stream().filter(account -> !account.equals(root) && subtree.contains(account))
                                .toList(),
                        child -> tr(cls("item depth-" + Math.min(book.depth(child), 3)),
                        td(a(href(Screen.STATEMENT.path() + "?account=" + child.identity()), text(Text.data(child.name())))),
                        td(cls("num"), book.children(child).isEmpty() || child.financial()
                                ? Ui.amount(book.balanceAs(child, day, type), false) : fragment()))));
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
                h3(text(message("Demonstração do resultado"))),
                p(cls("caption"), text(Text.data(Ui.month(month)))),
                table(cls("report-table"),
                        tbody(flows(book, AccountType.INCOME, month, income)),
                        tbody(flows(book, AccountType.EXPENSE, month, expense)),
                        tbody(cls("totals-row"),
                                tr(th(text(message(result.signum() < 0 ? "Déficit do mês" : "Superávit do mês"))),
                                        td(cls("num"), Ui.amount(result, true))))),
                when(income.signum() > 0, () -> p(cls("caption"), text(message("Taxa de poupança: "
                        + result.multiply(BigDecimal.valueOf(100)).divide(income, 0, RoundingMode.HALF_UP) + "%"))))
        );
    }

    private static Node flows(Book book, AccountType type, YearMonth month, BigDecimal total) {
        return book.root(type).map(root -> fragment(
                tr(cls("group"), th(text(Text.data(root.name()))), td(cls("num"), Ui.amount(total, false))),
                each(book.children(root), child -> share(book, child, month, total)))
        ).orElseGet(Html::fragment);
    }

    private static Node share(Book book, Account account, YearMonth month, BigDecimal total) {
        val flow = book.flow(account, month);
        return tr(cls("item"),
                td(a(href(Screen.STATEMENT.path() + "?account=" + account.identity() + "&" + Ledger.MONTH + "=" + month),
                                text(Text.data(account.name()))),
                        meter(min("0"), max(total.toPlainString()), value(flow.toPlainString()),
                                title(Text.data(Ui.money(flow))))),
                td(cls("num"), Ui.amount(flow, false)));
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
                h3(text(message("Evolução de gastos"))),
                p(cls("caption"), text(message(Ui.month(from) + " a " + Ui.month(months.getLast())
                        + " · % da receita do mês; média dos meses até " + Ui.month(current)))),
                div(cls("scroller"),
                        table(cls("report-table evolution outline"),
                                thead(tr(th(text(message("Conta"))),
                                        each(months, month -> th(cls("num"), text(Text.data(Ui.shortMonth(month))))),
                                        th(cls("num"), text(message("Total"))), th(cls("num"), text(message("Média"))))),
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
                                ariaLabel(message("Mostrar ou ocultar subcontas de " + account.name())),
                                span(cls("chevron"), ariaHidden(true)))),
                        text(Text.data(account.name()))),
                each(IntStream.range(0, SPAN).boxed().toList(), index -> td(cls("num"), values.get(index).signum() == 0
                        ? nothing()
                        : a(href(Screen.STATEMENT.path() + "?account=" + account.identity() + "&" + Ledger.MONTH + "="
                                        + evolution.months().get(index)),
                                share(values.get(index), evolution.revenue().get(index))))),
                td(cls("num total"), share(total, evolution.total())),
                td(cls("num"), average.signum() == 0 ? nothing() : Ui.amount(average, false))
        );
    }

    /** The value and its share of {@code base} (the income of the same months); a dash when zero. */
    private static Node share(BigDecimal value, BigDecimal base) {
        if (value.signum() == 0) return nothing();
        return fragment(Ui.amount(value, false), small(cls("share"), text(Text.data(base.signum() == 0 ? "—"
                : value.multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"))));
    }

    private static Node nothing() {
        return span(cls("caption"), text(Text.data("—")));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
