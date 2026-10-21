package br.bundled.money.web.report;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
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

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The balance sheet (Ativo = Passivo + PL + resultado acumulado) and the DRE of the month, each group with its
 * accounts one level down. Everything that changes with the month is inside {@code #reports}.
 */
@NullMarked
final class ReportViews {

    /** The closing form (INTENT F9): where it posts and its fields. */
    static final String CLOSING = "/reports/closing";
    static final String THRESHOLD = "threshold";
    static final String CONFIRM = "confirm";

    private ReportViews() {}

    static Node screen(Book book, YearMonth month) {
        return section(
                id("reports"), cls("reports"),
                div(cls("ledger-head"), h2(text(message("Relatórios"))),
                        Ledger.monthNav(month, YearMonth.from(book.today()), shown -> "/reports?month=" + shown, "#reports")),
                div(cls("report-grid"), balanceSheet(book, month.atEndOfMonth()), incomeStatement(book, month),
                        closing(book, null, null))
        );
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
        }).orElseGet(() -> fragment());
    }

    private static BigDecimal total(Book book, AccountType type, LocalDate day) {
        return book.root(type).map(root -> book.balance(root, day)).orElse(BigDecimal.ZERO);
    }

    // ---------- income statement

    /** Income and expenses of the month; each expense with a bar of its share of the total. */
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
                each(book.children(root).stream().filter(child -> book.flow(child, month).signum() != 0).toList(),
                        child -> share(book, child, month, total)))
        ).orElseGet(() -> fragment());
    }

    private static Node share(Book book, Account account, YearMonth month, BigDecimal total) {
        val flow = book.flow(account, month);
        return tr(cls("item"),
                td(a(href("/accounts/" + account.identity() + "?month=" + month), text(Text.data(account.name()))),
                        meter(min("0"), max(total.toPlainString()), value(flow.toPlainString()),
                                title(Text.data(Ui.money(flow))))),
                td(cls("num"), Ui.amount(flow, false)));
    }
}
