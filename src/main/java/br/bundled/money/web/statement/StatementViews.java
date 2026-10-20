package br.bundled.money.web.statement;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.LedgerLine;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.mock.MockTransaction;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Ui;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The statement screen: the list of accounts ({@code .statement-accounts}, always visible) and the panel
 * ({@code #statement-panel}) with the chosen account's month. The list loads only the panel; it marks the chosen
 * account itself, so its scroll is kept. The month travels to the list links in {@code #statement-month}.
 * "Novo lançamento" creates and each line edits or deletes its transaction in {@code #statement-editor}, a modal
 * dialog; saving or deleting answers with the panel and, out of band, the list with the new balances.
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

    static Node screen(Book book, MockAccount account, YearMonth month) {
        return section(
                cls("statement"),
                accounts(book, account, false),
                panel(book, account, month, fragment())
        );
    }

    /** The list; {@code oob}: sent beside the panel after a change, to update the balances. */
    static Node accounts(Book book, MockAccount account, boolean oob) {
        val balances = book.balances();
        return nav(id("statement-accounts"), cls("statement-accounts card"), ariaLabel(message("Contas")),
                hxOnHtmx("before-request", MARK), oob ? hxSwapOob() : fragment(),
                h3(text(message("Contas"))),
                ul(each(book.tree(), item -> li(accountLink(book, item, balances.getOrDefault(item.id(), BigDecimal.ZERO),
                        item.id() == account.id())))));
    }

    private static Node accountLink(Book book, MockAccount account, BigDecimal balance, boolean current) {
        val url = "/statement?account=" + account.id();
        val depth = book.depth(account);
        return a(href(url), hxGet(url), hxTarget("#statement-panel"), hxSwap(HxSwap.OUTER_HTML),
                hxInclude("#statement-month"), hxPushUrl(true),
                cls("depth-" + Math.min(depth, 3) + (depth == 0 ? " group" : "") + (current ? " current" : "")),
                span(text(Text.data(account.name()))),
                Ui.amount(balance, false));
    }

    /** The month of the account: navigation, summary and the transactions dated in the month. */
    static Node panel(Book book, MockAccount account, YearMonth month, Node editor) {
        val lines = book.ledger(account.id(), month);
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
                                        hxGet("/statement/transactions/new?account=" + account.id() + "&month=" + month),
                                        hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                        text(message("+ Novo lançamento"))),
                                Ledger.monthNav(month, YearMonth.from(book.today()),
                                        shown -> "/statement?account=" + account.id() + "&month=" + shown, "#statement-panel"))),
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
    private static Node line(Book book, LedgerLine line, MockAccount account, YearMonth month) {
        val transaction = line.transaction();
        val planned = transaction.date().isAfter(book.today());
        val closed = book.closed(transaction);
        val path = "/statement/transactions/" + transaction.id();
        val context = "?account=" + account.id() + "&month=" + month;
        return tr(
                cls(planned ? "planned" : ""),
                td(time(datetime(transaction.date().toString()), text(Text.data(Ui.day(transaction.date())))),
                        when(planned, () -> small(cls("sub"), text(message("previsto")))),
                        when(closed, () -> small(cls("sub"), text(message("fechado"))))),
                td(strong(text(Text.data(transaction.description()))),
                        small(cls("sub"), text(Text.data(line.counterpart())))),
                td(cls("num"), Ui.amount(line.amount(), true)),
                td(cls("num hide-narrow"), Ui.amount(line.balance(), false)),
                // fechado (antes da data-limite) é imutável: sem ações; com mais de duas contas (importado) só exclui,
                // o editor reescreve como de uma conta para outra
                td(cls("actions"), when(!closed, () -> fragment(
                        when(book.editable(transaction), () -> button(
                                type(ButtonType.BUTTON), cls("link icon-button"), hxGet(path + "/edit" + context),
                                hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                title(message("Editar")), ariaLabel(message("Editar " + transaction.description())),
                                span(cls("glyph glyph-edit"), ariaHidden(true)))),
                        button(type(ButtonType.BUTTON), cls("link danger icon-button"), hxGet(path + "/delete" + context),
                                hxTarget("#statement-editor"), hxSwap(HxSwap.INNER_HTML),
                                title(message("Excluir")), ariaLabel(message("Excluir " + transaction.description())),
                                span(cls("glyph glyph-delete"), ariaHidden(true))))))
        );
    }

    static Node notice(String text) {
        return p(cls("notice"), role("status"), text(message(text)));
    }

    // ---------- edit

    /**
     * Creating ({@code transaction == null}) or editing: the value from one account to another. The hidden account
     * and month re-render the panel.
     */
    static Node transactionForm(Book book, @Nullable MockTransaction transaction, Map<String, String> values,
                                List<Violation> errors, MockAccount account, YearMonth month) {
        val title = transaction == null ? "Novo lançamento" : "Editar lançamento";
        val accounts = options(book);
        return form(
                id("transaction-form"), cls("transaction-form card"), role("dialog"), ariaLabel(message(title)),
                method(FormMethod.POST), transaction == null ? action("/statement/transactions") : fragment(),
                transaction == null ? hxPost("/statement/transactions") : hxPut("/statement/transactions/" + transaction.id()),
                hxTarget("#statement-panel"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(message(title))),
                context(account, month),
                labeled(StatementFields.DATE, "Data", values, errors, null),
                labeled(StatementFields.AMOUNT, "Valor (R$)", values, errors, null, inputmode(InputMode.DECIMAL)),
                div(cls("wide"), labeled(StatementFields.DESCRIPTION, "Descrição", values, errors, null)),
                labeled(StatementFields.FROM, "De (sai de)", values, errors, accounts),
                labeled(StatementFields.TO, "Para (entra em)", values, errors, accounts),
                div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("Salvar"))),
                        cancel())
        );
    }

    // ---------- delete

    static Node deletePanel(Book book, MockTransaction transaction, MockAccount account, YearMonth month) {
        val label = message("Excluir \"" + transaction.description() + "\"");
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label),
                hxDelete("/statement/transactions/" + transaction.id()), hxTarget("#statement-panel"),
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

    private static Node context(MockAccount account, YearMonth month) {
        return fragment(input(type("hidden"), name("account"), value(String.valueOf(account.id()))),
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
                .forEach(account -> options.add(new Option(String.valueOf(account.id()), Text.data(book.path(account)))));
        return options;
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("transaction", field, label, values, errors, options, StatementFields::message, extra);
    }
}
