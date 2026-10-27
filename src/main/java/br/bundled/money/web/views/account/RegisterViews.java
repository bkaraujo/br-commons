package br.bundled.money.web.views.account;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.book.LedgerLine;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Ui;
import br.bundled.money.web.views.tools.Formatters;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Option;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HxSwap;
import br.commons.lang.htmx.ScrollEdge;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import br.commons.web.ui.FontAwesome;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.bundled.money.web.views.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The chart of accounts with its register: "Nova conta" and, on each row below the top level, edit and delete. The
 * form and the delete panel open in {@code #account-editor}, shown as a modal dialog over a frosted tree (CSS: the
 * editor holding a card); Escape or a click on the backdrop empties it. Three parts change alone: the head
 * ({@code #accounts-head}), only by the closing; the tree ({@code #accounts-tree}), by saving, deleting and the empty
 * book, with a notice in the editor and the panel out of band; beside the tree, {@code #account-panel}: the month of
 * the account picked in it (summary, transactions), read only, the only thing a name in the tree loads, with the
 * month ({@code #account-month}). The month navigation reloads the whole screen.
 */
@NullMarked
final class RegisterViews {

    /** The id of the month beside the tree: a name in the tree loads only it. */
    static final String PANEL = "account-panel";

    /** The head of the chart (title, closing button, actions): only the closing swaps it. */
    static final String HEAD = "accounts-head";

    /** The tree: saving, deleting and the empty book swap it. */
    static final String TREE = "accounts-tree";

    /** Loading into the editor scrolls it into view: a row may be far below. */
    private static final SwapSpec INTO_EDITOR = SwapSpec.of(HxSwap.INNER_HTML).show("#account-editor", ScrollEdge.TOP);

    /** Closing the dialog: a click on the backdrop itself, or Escape while a card is open. */
    private static final String DISMISS = "click[target.id=='account-editor'], "
            + "keyup[key=='Escape'&&document.querySelector('#account-editor > .card')!=null] from:body";

    /** The id prefix of each top-level branch of the tree ({@code tree.js} keeps the open ones by it). */
    private static final String BRANCH = "branch-";

    /** Moves {@code .current} to the row whose name was clicked: the tree is not re-rendered. */
    private static final String MARK = "if (event.target.closest('a')) { this.querySelectorAll('tr.current')"
            + ".forEach(row => row.classList.remove('current')); event.target.closest('tr').classList.add('current') }";

    private RegisterViews() {}

    /** The tree and, beside it, the month of {@code account}. */
    static Node layout(Book book, Node editor, Account account, YearMonth month) {
        return div(cls("chart"), screen(book, editor, account), panel(book, account, month, false));
    }

    /** The head, the editor and the tree; {@code current}: the account whose month is beside it. */
    static Node screen(Book book, Node editor, Account current) {
        return section(id("accounts"), cls("registry"), head(book, false),
                div(id("account-editor"), cls("modal"), hxGet("/accounts/editor"), hxTrigger(DISMISS),
                        hxSwap(HxSwap.INNER_HTML), editor),
                tree(book, current));
    }

    /**
     * The title with the closing button, and the actions: only the closing changes it ({@link #closed}); {@code oob}:
     * beside the tree, when the empty book resets the closing.
     */
    static Node head(Book book, boolean oob) {
        return div(id(HEAD), cls("ledger-head"), oob ? hxSwapOob() : fragment(),
                div(cls("title-line"), h2(text(message("forms.account.chart"))), closingButton(book)),
                div(cls("buttons"),
                        button(type(ButtonType.BUTTON), cls("link"), hxGet(EMPTY), hxTarget("#account-editor"),
                                hxSwap(INTO_EDITOR), text(message("forms.account.new_empty_book"))),
                        button(type(ButtonType.BUTTON), hxGet("/accounts/new"), hxTarget("#account-editor"),
                                hxSwap(INTO_EDITOR), text(message("forms.account.new_account_button")))));
    }

    /** The tree: what saving, deleting and the empty book swap; a name loads only the panel ({@link #PANEL}). */
    static Node tree(Book book, Account current) {
        val balances = book.balances();
        return table(
                id(TREE), cls("records tree"), hxOnHtmx("before-request", MARK),
                thead(tr(th(text(message("forms.common.account"))), th(text(message("forms.account.type"))),
                        th(cls("num"), text(message("forms.common.balance"))), th(ariaLabel(message("forms.common.actions"))))),
                each(branches(book), branch -> {
                    val rows = each(branch, account -> row(book, account, balances.getOrDefault(account, BigDecimal.ZERO),
                            account.equals(current)));
                    return branch.size() > 1 ? tbody(id(BRANCH + branch.getFirst().identity()), cls("branch collapsed"), rows) : tbody(rows);
                })
        );
    }

    /** The editor's content out of band: a notice after a write, or nothing (the dialog closes). */
    static Node editorContent(Node content) {
        return div(hxSwapOob(HxSwap.INNER_HTML, "#account-editor"), content);
    }

    /**
     * The month of the account: summary and its transactions, each value without sign (the month navigation is in
     * the header); {@code oob}: sent beside the tree after a change.
     */
    static Node panel(Book book, Account account, YearMonth month, boolean oob) {
        val lines = book.ledger(account, month);
        return div(
                id(PANEL), cls("statement-panel account-panel"),
                oob ? hxSwapOob() : fragment(),
                input(type("hidden"), id("account-month"), name(Ledger.MONTH), value(month.toString())),
                div(cls("ledger-head"),
                        div(p(cls("caption"), text(message("forms.account.entries_caption", Ui.typeLabel(account.type())))),
                                h2(text(Text.data(book.path(account))))),
                        div(cls("buttons"),
                                a(href(Screen.STATEMENT.path() + "?account=" + account.identity() + "&" + Ledger.MONTH + "="
                                        + month), text(message("forms.account.open_statement"))))),
                Ledger.summary(book, account, month, lines),
                div(cls("ledger-scroll"), table(
                        cls("records ledger-table"),
                        thead(tr(th(text(message("forms.common.date"))), th(text(message("forms.common.description"))),
                                th(cls("num"), text(message("forms.common.value"))))),
                        tbody(
                                each(lines, line -> line(book, line, account, month)),
                                when(lines.isEmpty(), () -> tr(cls("empty"),
                                        td(colspan(3), text(message("forms.account.no_entries", Formatters.month(month)))))))
                ))
        );
    }

    /** A line dated after today is planned: still counted, shown apart. */
    private static Node line(Book book, LedgerLine line, Account account, YearMonth month) {
        val transaction = line.transaction();
        val planned = transaction.date().isAfter(book.today());
        return tr(
                cls(planned ? "planned" : ""),
                td(time(datetime(transaction.date().toString()), text(Text.data(Formatters.day(transaction.date())))),
                        when(planned, () -> small(cls("sub"), text(message("forms.common.planned"))))),
                Ledger.described(transaction, line.counterpart(), account, month),
                td(cls("num"), Formatters.amount(line.amount().abs(), false))
        );
    }

    /**
     * The tree cut at each top-level account (Ativo, Passivo...): one {@code <tbody>} each; one with subaccounts is a
     * branch born collapsed, the group row with the button that opens it ({@code static/money/tree.js}, which
     * remembers the open ones).
     */
    private static List<List<Account>> branches(Book book) {
        val branches = new ArrayList<List<Account>>();
        for (val account : book.tree()) {
            if (account.parent() == null) branches.add(new ArrayList<>());
            branches.getLast().add(account);
        }
        return branches;
    }

    /** A row; the top level (Ativo, Passivo...) is fixed: no actions. */
    private static Node row(Book book, Account account, BigDecimal balance, boolean current) {
        val depth = book.depth(account);
        val path = "/accounts/" + account.identity();
        val month = Screen.ACCOUNTS.path() + "?account=" + account.identity();
        return tr(
                cls("depth-" + Math.min(depth, 3) + (depth == 0 ? " group" : "") + (current ? " current" : "")),
                td(when(depth == 0 && !book.children(account).isEmpty(), () -> button(
                                type(ButtonType.BUTTON), cls("toggle"), ariaExpanded(false), ariaControls(BRANCH + account.identity()),
                                ariaLabel(message("forms.account.toggle_subaccounts")), span(cls("chevron"), ariaHidden(true)))),
                        a(href(month), hxGet(month), hxTarget("#" + PANEL), hxSwap(HxSwap.OUTER_HTML),
                                hxInclude("#account-month"), hxPushUrl(true),
                                text(Text.data(account.name())))),
                td(span(cls("badge" + (account.financial() ? " financial" : "")),
                        text(Text.message(Ui.typeLabel(account.type()))))),
                td(cls("num"), Formatters.amount(balance, false)),
                td(cls("actions"), when(depth > 0, () -> fragment(
                        button(type(ButtonType.BUTTON), cls("link icon-button"), hxGet(path + "/edit"),
                                hxTarget("#account-editor"), hxSwap(INTO_EDITOR),
                                title(message("forms.common.edit")), ariaLabel(message("forms.account.edit_named", account.name())),
                                span(cls("glyph glyph-edit"), ariaHidden(true))),
                        button(type(ButtonType.BUTTON), cls("link danger icon-button"), hxGet(path + "/delete"),
                                hxTarget("#account-editor"), hxSwap(INTO_EDITOR),
                                title(message("forms.common.delete")), ariaLabel(message("forms.account.delete_named", account.name())),
                                span(cls("glyph glyph-delete"), ariaHidden(true))))))
        );
    }

    static Node notice(Text text) {
        return p(cls("notice"), role("status"), text(text));
    }

    // ---------- form

    /**
     * Creating ({@code current == null}) or editing. Changing the type reloads the form ({@code GET /accounts/form})
     * with the parents and the fields of the new type. With transactions or subaccounts the type is fixed.
     */
    static Node accountForm(Book book, Map<String, String> values, List<Violation> errors, @Nullable Account current) {
        val locked = current != null && book.fixedType(current);
        val type = locked ? Objects.requireNonNull(current).type() : AccountFields.type(values);
        val financial = AccountFields.FINANCIAL.contains(type);
        val title = formTitle(current);
        return form(
                id("account-form"), cls("account-form card"), role("dialog"), ariaLabel(title),
                method(FormMethod.POST), action(current == null ? "/accounts" : "/accounts/" + current.identity()),
                current == null ? hxPost("/accounts") : hxPut("/accounts/" + current.identity()),
                hxTarget("#" + TREE), hxSwap(HxSwap.OUTER_HTML),
                h3(text(title)),
                editing(current),
                typeField(locked, type, errors),
                labeled(AccountFields.PARENT, "forms.account.parent", values, errors, parents(book, type, current)),
                labeled(AccountFields.NAME, "forms.account.name", values, errors, null),
                when(current == null && financial, () -> labeled(AccountFields.OPENING, "forms.account.opening", values,
                        errors, null, cls("amount-input"), data("money", "signed"), placeholder(Text.data("0,00")))),
                div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("forms.common.save"))),
                        cancel())
        );
    }

    private static Text formTitle(@Nullable Account current) {
        return current == null ? message("forms.account.new_account") : message("forms.account.edit_named", current.name());
    }

    /** The account being edited, so the update knows which one. */
    private static Node editing(@Nullable Account current) {
        return current == null ? fragment() : input(type("hidden"), name("editing"), value(current.identity().toString()));
    }

    /** The type: fixed (with transactions or subaccounts) or a dropdown that reloads the form. */
    private static Node typeField(boolean locked, AccountType type, List<Violation> errors) {
        return locked
                ? div(cls("field"), label(text(message("forms.account.type"))),
                        p(cls("fixed"), text(message("forms.account.type_fixed", Ui.typeLabel(type)))),
                        input(type("hidden"), name(AccountFields.TYPE.name()), value(type.name())))
                : labeled(AccountFields.TYPE, "forms.account.type", Map.of(AccountFields.TYPE.name(), type.name()), errors,
                        types(), hxGet("/accounts/form"), hxTrigger("change"), hxTarget("#account-form"),
                        hxSwap(HxSwap.OUTER_HTML), hxInclude("closest form"));
    }

    private static List<Option> types() {
        val options = new ArrayList<Option>();
        AccountFields.TYPES.forEach(type -> options.add(new Option(type.name(), Text.message(Ui.typeLabel(type)))));
        return options;
    }

    private static List<Option> parents(Book book, AccountType type, @Nullable Account current) {
        return accounts(book, book.parents(type, current));
    }

    private static List<Option> accounts(Book book, List<Account> accounts) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("forms.common.choose")));
        accounts.forEach(account -> options.add(new Option(account.identity().toString(), Text.data(book.path(account)))));
        return options;
    }

    // ---------- delete

    /**
     * Deleting: an account with subaccounts is refused; one with transactions asks where to move them (INTENT §6);
     * one without just confirms.
     */
    static Node deletePanel(Book book, Account account, Map<String, String> values, List<Violation> errors) {
        val uses = book.uses(account);
        val label = message("forms.account.delete_named", book.path(account));
        val title = h3(text(label));
        if (!book.children(account).isEmpty()) {
            return div(cls("delete-panel card"), role("dialog"), ariaLabel(label), title,
                    p(text(message("forms.account.has_subaccounts"))),
                    div(cls("buttons"), cancel()));
        }
        if (book.closedUses(account)) {
            return div(cls("delete-panel card"), role("dialog"), ariaLabel(label), title,
                    p(text(message("forms.account.closed_uses"))),
                    div(cls("buttons"), button(type(ButtonType.BUTTON), hxGet(CLOSING), hxTarget("#account-editor"),
                            hxSwap(INTO_EDITOR), text(message("forms.closing.change"))), cancel()));
        }
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label), hxDelete("/accounts/" + account.identity()),
                hxTarget("#" + TREE),
                hxSwap(HxSwap.OUTER_HTML),
                title,
                uses > 0
                        ? fragment(p(text(message("forms.account.uses_moved", uses))),
                                labeled(AccountFields.TARGET, "forms.account.move_to", values, errors,
                                        accounts(book, book.targets(account))))
                        : p(text(message("forms.account.no_uses"))),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text((uses > 0 ? message("forms.account.move_and_delete") : message("forms.common.delete")))),
                        cancel())
        );
    }

    /** The empty book from the personal chart of accounts (INTENT F8): GET the confirmation, POST to create it. */
    static final String EMPTY = "/accounts/empty";

    /** Starting over discards everything in memory, so it is confirmed first; no file is touched. */
    static Node emptyPanel(Book book, int template) {
        val label = message("forms.account.new_empty_book");
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label), method(FormMethod.POST), action(EMPTY),
                hxPost(EMPTY), hxTarget("#" + TREE), hxSwap(HxSwap.OUTER_HTML),
                h3(text(label)),
                p(text(message("forms.account.empty_template", template))),
                p(text(message("forms.account.empty_discard", book.tree().size(), book.count()))),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text(message("forms.account.create_empty"))),
                        cancel())
        );
    }

    // ---------- closing (INTENT F9)

    static final String CLOSING = AccountRoutes.CLOSING;
    static final String MONTH = "month";
    static final String CONFIRM = "confirm";
    static final String REMOVE = "remove";

    /**
     * The closing is by month: closing a month closes everything up to its last day, so the closing date is the first
     * day of the next one. The last month closed, or null when nothing is.
     */
    static @Nullable YearMonth closedMonth(Book book) {
        val threshold = book.threshold();
        return threshold == null ? null : YearMonth.from(threshold.minusDays(1));
    }

    /** The first day open once {@code month} is closed: the closing date of the book. */
    static LocalDate threshold(YearMonth month) {
        return month.plusMonths(1).atDay(1);
    }

    /** {@code 2025/10}: the month on the button, year first. */
    private static Text yearMonth(YearMonth month) {
        return Text.data(month.getYear() + "/" + String.format("%02d", month.getMonthValue()));
    }

    /** After the closing: the head with the new month and, out of band, the editor emptied (the dialog closes). */
    static Node closed(Book book) {
        return fragment(head(book, false), editorContent(fragment()));
    }

    /** The button of the closing dialog: a lock and the last month closed (open, when nothing is). */
    private static Node closingButton(Book book) {
        val closed = closedMonth(book);
        val heading = message("forms.closing.title");
        return button(type(ButtonType.BUTTON), cls("link closing-button"), hxGet(CLOSING), hxTarget("#account-editor"),
                hxSwap(INTO_EDITOR), title(heading), ariaLabel(heading),
                span(cls("icon"), ariaHidden(true), (closed == null ? FontAwesome.Solid.LOCK_OPEN : FontAwesome.Solid.LOCK).svg()),
                span(text(closed == null ? message("forms.closing.button_none") : yearMonth(closed))));
    }

    /**
     * The closing month: every transaction up to its end is immutable. {@code reopening}: the month asked, earlier
     * than the current one (or none, removing the closing), waiting for the confirmation; moving it back is an explicit
     * act. {@code error}: why the month was refused. Saving swaps only the head ({@link #closed}).
     */
    static Node closingPanel(Book book, @Nullable String reopening, @Nullable Text error) {
        val closed = closedMonth(book);
        val shown = reopening != null ? reopening : closed == null ? "" : closed.toString();
        val heading = message("forms.closing.title");
        return form(
                cls("closing-form card"), role("dialog"), ariaLabel(heading), method(FormMethod.POST), action(CLOSING),
                hxPost(CLOSING), hxTarget("#" + HEAD), hxSwap(HxSwap.OUTER_HTML),
                h3(text(heading)),
                p(cls("caption"), text(closed == null ? message("forms.closing.none_closed")
                        : message("forms.closing.closed_before", Formatters.month(closed)))),
                label(text(message("forms.closing.month")),
                        reopening == null ? input(type("month"), name(MONTH), value(shown))
                                : input(type("month"), name(MONTH), value(shown), readonly())),
                error == null ? fragment() : p(cls("error"), role("alert"), text(error)),
                reopening == null ? saveButtons(closed != null) : confirmation(reopening)
        );
    }

    /** The buttons of the closing dialog: save and, with a month closed, remove it. */
    private static Node saveButtons(boolean closed) {
        return div(cls("buttons"), button(type(ButtonType.SUBMIT), text(message("forms.common.save"))),
                when(closed, () -> button(type(ButtonType.SUBMIT), cls("link danger"), name(REMOVE),
                        value("1"), text(message("forms.closing.remove")))),
                cancel());
    }

    /** Moving the closing back (or removing it; {@code reopening} empty) waits for an explicit confirmation. */
    private static Node confirmation(String reopening) {
        val removing = reopening.isEmpty();
        return fragment(
                p(cls("error"), role("alert"), text(message(removing
                        ? "forms.closing.remove_warning" : "forms.closing.reopen_warning"))),
                input(type("hidden"), name(CONFIRM), value("1")),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text(message(removing
                                ? "forms.closing.confirm_remove" : "forms.closing.confirm_reopen"))),
                        cancel()));
    }

    private static Node cancel() {
        return button(type(ButtonType.BUTTON), cls("link"), hxGet("/accounts/editor"), hxTarget("#account-editor"),
                hxSwap(HxSwap.INNER_HTML), text(message("forms.common.cancel")));
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("account", field, label, values, errors, options, AccountFields::message, extra);
    }
}
