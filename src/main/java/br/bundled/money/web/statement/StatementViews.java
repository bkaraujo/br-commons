package br.bundled.money.web.statement;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.book.LedgerLine;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Option;
import br.commons.lang.html.form.Violation;
import br.commons.lang.html.html5.InputMode;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The statement screen: the home's accounts and cards ({@code .statement-accounts}, always visible) and the panel
 * ({@code #statement-panel}) with the chosen account's month. The list loads only the panel; it marks the chosen
 * account itself, so its scroll is kept. The month travels to the list links in {@code #statement-month}.
 * "Novo lançamento" creates and each line edits or deletes its transaction in {@code #statement-editor}, a modal
 * dialog; saving or deleting answers with the panel and, out of band, the list with the new balances. A transaction
 * from one account to another uses the simple form; "Dividir em várias contas" turns it into the form of splits
 * (INTENT F2), where any other one is edited.
 */
@NullMarked
final class StatementViews {

    /** Moves {@code .current} to the link that asked: the list is not re-rendered. */
    private static final String MARK = "if (event.target.closest('a')) { this.querySelectorAll('a.current')"
            + ".forEach(link => link.classList.remove('current')); event.target.closest('a').classList.add('current') }";

    /** Closing the dialog: a click on the backdrop itself, or Escape while a card is open. */
    private static final String DISMISS = "click[target.id=='statement-editor'], "
            + "keyup[key=='Escape'&&document.querySelector('#statement-editor > .card')] from:body";

    private StatementViews() {}

    /** The list and the panel of the account's month, {@code editor} in its editor (a notice, a form or nothing). */
    static Node screen(Book book, Account account, YearMonth month, Node editor) {
        return section(
                cls("statement"),
                accounts(book, account, month, false),
                panel(book, account, month, editor)
        );
    }

    /**
     * The list: the accounts and the credit cards of the home ({@link Book#moneyAccounts}), each group under its title,
     * an empty one left out, each with its value in {@code month}: the balance of an account ({@link #shown}), the bill
     * of a card ({@link Book#bills}); {@code oob}: sent beside the panel after a change or a new month.
     */
    static Node accounts(Book book, Account account, YearMonth month, boolean oob) {
        val cards = book.cards();
        val values = new HashMap<>(book.balances(book.bankAccounts(), shown(book, month)));
        values.putAll(book.bills(cards, month));
        return nav(id("statement-accounts"), cls("statement-accounts card"), ariaLabel(message("Contas")),
                hxOnHtmx("before-request", MARK), oob ? hxSwapOob() : fragment(),
                group("Contas", book.bankAccounts(), values, account),
                group("Cartões", cards, values, account));
    }

    /** The day of the balances: today in the current month (planned entries do not count yet), else its last day. */
    private static LocalDate shown(Book book, YearMonth month) {
        return YearMonth.from(book.today()).equals(month) ? book.today() : month.atEndOfMonth();
    }

    private static Node group(String title, List<Account> accounts, Map<Account, BigDecimal> values, Account account) {
        return accounts.isEmpty() ? fragment() : fragment(h3(text(message(title))),
                ul(each(accounts, item -> li(accountLink(item, values.getOrDefault(item, BigDecimal.ZERO), item.equals(account),
                        depth(item, accounts))))));
    }

    /** How many accounts of the list are above this one (an account with subaccounts), up to the 3 the style has. */
    private static int depth(Account account, List<Account> listed) {
        var depth = 0;
        for (var parent = account.parent(); parent != null; parent = parent.parent()) {
            if (listed.contains(parent)) depth++;
        }
        return Math.min(depth, 3);
    }

    private static Node accountLink(Account account, BigDecimal balance, boolean current, int depth) {
        val url = "/statement?account=" + account.identity();
        return a(href(url), hxGet(url), hxTarget("#statement-panel"), hxSwap(HxSwap.OUTER_HTML),
                hxInclude("#statement-month"), hxPushUrl(true),
                cls((current ? "current" : "") + (depth > 0 ? " depth-" + depth : "")),
                span(text(Text.data(account.name()))),
                span(Ui.amount(balance, false)));
    }

    /** The month of the account: navigation, summary and the transactions dated in the month. */
    static Node panel(Book book, Account account, YearMonth month, Node editor) {
        val lines = book.ledger(account, month);
        return div(
                id("statement-panel"), cls("statement-panel"),
                input(type("hidden"), id("statement-month"), name("month"), value(month.toString())),
                div(id("statement-editor"), cls("modal"), hxGet("/statement/editor"), hxTrigger(DISMISS),
                        hxSwap(HxSwap.INNER_HTML), editor),
                div(cls("ledger-head"),
                        div(p(cls("caption"), text(message("Extrato · " + Ui.typeLabel(account.type())))),
                                h2(text(Text.data(book.path(account))))),
                        div(cls("buttons"),
                                button(type(ButtonType.BUTTON),
                                        hxGet("/statement/transactions/new?account=" + account.identity() + "&month=" + month),
                                        hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                        text(message("+ Novo lançamento"))))),
                Ledger.summary(book, account, month, lines),
                div(cls("ledger-scroll"), table(
                        cls("records ledger-table"),
                        Ledger.columns(th(ariaLabel(message("Ações")))),
                        tbody(
                                each(lines, line -> line(book, line, account, month)),
                                when(lines.isEmpty(), () -> tr(cls("empty"),
                                        td(colspan(5), text(message("Nenhum lançamento previsto para " + Ui.month(month) + "."))))))
                ))
        );
    }

    /** A line dated after today is planned: still counted, shown apart. */
    private static Node line(Book book, LedgerLine line, Account account, YearMonth month) {
        val transaction = line.transaction();
        val planned = transaction.date().isAfter(book.today());
        val closed = book.closed(transaction);
        val path = "/statement/transactions/" + transaction.identity();
        val context = "?account=" + account.identity() + "&month=" + month;
        val shown = shownDate(book, transaction, account);
        return tr(
                cls(planned ? "planned" : ""),
                td(time(datetime(shown.toString()), text(Text.data(Ui.day(shown)))),
                        when(planned, () -> small(cls("sub"), text(message("previsto")))),
                        when(closed, () -> small(cls("sub"), text(message("fechado"))))),
                td(strong(text(Text.data(transaction.description()))),
                        small(cls("sub"), text(Text.data(line.counterpart())))),
                td(cls("num"), Ui.amount(line.amount(), true)),
                td(cls("num hide-narrow"), Ui.amount(line.balance(), false)),
                // fechado (antes da data-limite) é imutável: sem ações
                td(cls("actions"), when(!closed, () -> fragment(
                        button(type(ButtonType.BUTTON), cls("link icon-button"), hxGet(path + "/edit" + context),
                                hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                title(message("Editar")), ariaLabel(message("Editar " + transaction.description())),
                                span(cls("glyph glyph-edit"), ariaHidden(true))),
                        button(type(ButtonType.BUTTON), cls("link danger icon-button"), hxGet(path + "/delete" + context),
                                hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                title(message("Excluir")), ariaLabel(message("Excluir " + transaction.description())),
                                span(cls("glyph glyph-delete"), ariaHidden(true))))))
        );
    }

    /** In a card's statement, a purchase is dated by the day it was made ({@link Transaction#purchased()}), not the bill's. */
    private static LocalDate shownDate(Book book, Transaction transaction, Account account) {
        val purchased = transaction.purchased();
        return purchased != null && book.cards().contains(account) ? purchased : transaction.date();
    }

    static Node notice(String text) {
        return p(cls("notice"), role("status"), text(message(text)));
    }

    // ---------- edit

    /**
     * Creating ({@code transaction == null}) or editing: the value from one account to another. The hidden account
     * and month re-render the panel; "Dividir" takes the values to the form of splits.
     */
    static Node transactionForm(Book book, @Nullable Transaction transaction, Map<String, String> values,
                                List<Violation> errors, Account account, YearMonth month) {
        val title = transaction == null ? "Novo lançamento" : "Editar lançamento";
        val accounts = options(book);
        return form(
                id("transaction-form"), cls("transaction-form card"), role("dialog"), ariaLabel(message(title)),
                method(FormMethod.POST), transaction == null ? action("/statement/transactions") : fragment(),
                transaction == null ? hxPost("/statement/transactions") : hxPut("/statement/transactions/" + transaction.identity()),
                hxTarget("#statement-panel"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(message(title))),
                context(account, month), editing(transaction),
                labeled(StatementFields.DATE, "Data", values, errors, null),
                labeled(StatementFields.AMOUNT, "Valor (R$)", values, errors, null, inputmode(InputMode.DECIMAL)),
                div(cls("wide"), labeled(StatementFields.DESCRIPTION, "Descrição", values, errors, null)),
                labeled(StatementFields.FROM, "De (sai de)", values, errors, accounts),
                labeled(StatementFields.TO, "Para (entra em)", values, errors, accounts),
                div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("Salvar"))),
                        cancel(),
                        button(type(ButtonType.BUTTON), cls("link split-toggle"), hxGet(SPLIT), hxInclude("closest form"),
                                hxTarget("#transaction-form"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Dividir em várias contas"))))
        );
    }

    /** Re-renders the form of splits from the values sent along: {@code ?add=1} adds a line, {@code ?remove=2} one. */
    static final String SPLIT = "/statement/transactions/split";

    /**
     * The complete transaction (INTENT F2): a line per split, the account and the value that enters or leaves it.
     * Lines are added and removed by the server ({@link #SPLIT}); {@code splits.js} shows the totals as they are typed.
     */
    static Node splitForm(Book book, @Nullable Transaction transaction, Map<String, String> values,
                          List<SplitFields.Line> lines, List<Violation> errors, Account account, YearMonth month) {
        val title = transaction == null ? "Novo lançamento" : "Editar lançamento";
        val accounts = options(book);
        val in = sum(lines, SplitFields.Line::in);
        val out = sum(lines, SplitFields.Line::out);
        return form(
                id("transaction-form"), cls("transaction-form split-form card"), role("dialog"), ariaLabel(message(title)),
                method(FormMethod.POST), transaction == null ? action("/statement/transactions") : fragment(),
                transaction == null ? hxPost("/statement/transactions") : hxPut("/statement/transactions/" + transaction.identity()),
                hxTarget("#statement-panel"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(message(title))),
                context(account, month), editing(transaction),
                input(type("hidden"), name(SplitFields.MODE), value(SplitFields.SPLITS)),
                labeled(StatementFields.DATE, "Data", values, errors, null),
                div(cls("wide"), labeled(StatementFields.DESCRIPTION, "Descrição", values, errors, null)),
                div(cls("wide split-lines"), table(
                        cls("splits"),
                        thead(tr(th(text(message("Conta"))), th(cls("num"), text(message("Entra (R$)"))),
                                th(cls("num"), text(message("Sai (R$)"))), th(ariaLabel(message("Ações"))))),
                        tbody(each(indexes(lines), index -> splitLine(lines.get(index), index, lines.size() > 2,
                                accounts, errors))),
                        tfoot(tr(th(text(message("Total"))),
                                td(cls("num"), output(cls("split-sum-in"), text(Text.data(Ui.money(in))))),
                                td(cls("num"), output(cls("split-sum-out"), text(Text.data(Ui.money(out))))),
                                td()))),
                        p(cls("split-balance" + (in.compareTo(out) == 0 ? "" : " neg")), role("status"),
                                text(Text.data(difference(in, out)))),
                        Ui.errorOf(SplitFields.TOTAL_FIELD, errors, SplitFields::message)),
                div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("Salvar"))),
                        cancel(),
                        button(type(ButtonType.BUTTON), cls("link"), hxGet(SPLIT + "?add=1"), hxInclude("closest form"),
                                hxTarget("#transaction-form"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("+ Linha"))))
        );
    }

    private static Node splitLine(SplitFields.Line line, int index, boolean removable, List<Option> accounts,
                                  List<Violation> errors) {
        val error = errors.stream().filter(violation -> violation.field().equals(SplitFields.line(index))).findFirst();
        return tr(
                td(select(DIALECT, SplitFields.ACCOUNT, accounts, Set.of(line.account()),
                        id("split-account-" + index), ariaLabel(message("Conta"))),
                        error.map(violation -> (Node) small(cls("error"), role("alert"),
                                text(message(SplitFields.message(violation))))).orElseGet(Html::fragment)),
                td(cls("num"), input(DIALECT, SplitFields.IN, line.in(), inputmode(InputMode.DECIMAL),
                        ariaLabel(message("Entra (R$)")))),
                td(cls("num"), input(DIALECT, SplitFields.OUT, line.out(), inputmode(InputMode.DECIMAL),
                        ariaLabel(message("Sai (R$)")))),
                td(cls("actions"), when(removable, () -> button(
                        type(ButtonType.BUTTON), cls("link danger icon-button"), hxGet(SPLIT + "?remove=" + index),
                        hxInclude("closest form"), hxTarget("#transaction-form"), hxSwap(HxSwap.OUTER_HTML),
                        title(message("Remover linha")), ariaLabel(message("Remover linha")),
                        span(cls("glyph glyph-delete"), ariaHidden(true)))))
        );
    }

    private static List<Integer> indexes(List<?> list) {
        return IntStream.range(0, list.size()).boxed().toList();
    }

    /** Σ of a column, the values that are not a number left out (the server says what is wrong on saving). */
    private static BigDecimal sum(List<SplitFields.Line> lines, Function<SplitFields.Line, String> column) {
        return lines.stream().map(line -> SplitFields.amount(column.apply(line))).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** As {@code splits.js} writes it while typing. */
    private static String difference(BigDecimal in, BigDecimal out) {
        return in.compareTo(out) == 0 ? "Balanceado." : "Diferença: " + Ui.money(in.subtract(out).abs()) + ".";
    }

    /** The transaction being edited, so "Dividir" keeps saving it. */
    private static Node editing(@Nullable Transaction transaction) {
        return transaction == null ? fragment()
                : input(type("hidden"), name(StatementFields.EDITING), value(transaction.identity().toString()));
    }

    // ---------- delete

    static Node deletePanel(Book book, Transaction transaction, Account account, YearMonth month) {
        val label = message("Excluir \"" + transaction.description() + "\"");
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label),
                hxDelete("/statement/transactions/" + transaction.identity()), hxTarget("#statement-panel"),
                hxSwap(HxSwap.OUTER_HTML),
                h3(text(label)),
                context(account, month),
                p(text(message(Ui.fullDay(transaction.date()) + " · " + Ui.money(book.value(transaction))
                        + " · " + book.label(transaction) + ". O lançamento some de todas as contas."))),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text(message("Excluir"))),
                        cancel())
        );
    }

    private static Node context(Account account, YearMonth month) {
        return fragment(input(type("hidden"), name("account"), value(account.identity().toString())),
                input(type("hidden"), name("month"), value(month.toString())));
    }

    private static Node cancel() {
        return button(type(ButtonType.BUTTON), cls("link"), hxGet("/statement/editor"), hxTarget("#statement-editor"),
                hxSwap(HxSwap.INNER_HTML), text(message("Cancelar")));
    }

    /** The accounts below the top level, by their path. */
    private static List<Option> options(Book book) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("Escolha…")));
        book.tree().stream().filter(account -> account.parent() != null)
                .forEach(account -> options.add(new Option(account.identity().toString(), Text.data(book.path(account)))));
        return options;
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("transaction", field, label, values, errors, options, StatementFields::message, extra);
    }
}
