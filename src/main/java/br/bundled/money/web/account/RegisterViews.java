package br.bundled.money.web.account;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Screen;
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
import br.commons.lang.htmx.ScrollEdge;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The chart of accounts with its register: "Nova conta" and, on each row below the top level, edit and delete. The
 * form and the delete panel open in {@code #account-editor}, shown as a modal dialog over a frosted tree (CSS: the
 * editor holding a card); Escape or a click on the backdrop empties it. Saving or deleting answers with the whole
 * {@code #accounts}, the tree updated and a notice in the editor.
 */
@NullMarked
final class RegisterViews {

    /** Loading into the editor scrolls it into view: a row may be far below. */
    private static final SwapSpec INTO_EDITOR = SwapSpec.of(HxSwap.INNER_HTML).show("#account-editor", ScrollEdge.TOP);

    /** Closing the dialog: a click on the backdrop itself, or Escape while a card is open. */
    private static final String DISMISS = "click[target.id=='account-editor'], "
            + "keyup[key=='Escape'&&document.querySelector('#account-editor > .card')] from:body";

    /** The id prefix of each top-level branch of the tree ({@code tree.js} keeps the open ones by it). */
    private static final String BRANCH = "branch-";

    private RegisterViews() {}

    static Node screen(Book book, Node editor) {
        val balances = book.balances();
        return section(
                id("accounts"), cls("registry"),
                div(cls("ledger-head"),
                        h2(text(message("Plano de contas"))),
                        div(cls("buttons"),
                                button(type(ButtonType.BUTTON), cls("link"), hxGet(EMPTY), hxTarget("#account-editor"),
                                        hxSwap(INTO_EDITOR), text(message("Novo livro vazio"))),
                                button(type(ButtonType.BUTTON), hxGet("/accounts/new"), hxTarget("#account-editor"),
                                        hxSwap(INTO_EDITOR), text(message("+ Nova conta"))))),
                div(id("account-editor"), cls("modal"), hxGet("/accounts/editor"), hxTrigger(DISMISS),
                        hxSwap(HxSwap.INNER_HTML), editor),
                table(
                        cls("records tree"),
                        thead(tr(th(text(message("Conta"))), th(cls("hide-narrow"), text(message("Tipo"))),
                                th(cls("num"), text(message("Saldo"))), th(ariaLabel(message("Ações"))))),
                        each(branches(book), branch -> {
                            val rows = each(branch, account -> row(book, account, balances.getOrDefault(account, BigDecimal.ZERO)));
                            return branch.size() > 1 ? tbody(id(BRANCH + branch.getFirst().identity()), cls("branch collapsed"), rows) : tbody(rows);
                        })
                )
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
    private static Node row(Book book, Account account, BigDecimal balance) {
        val depth = book.depth(account);
        val path = "/accounts/" + account.identity();
        return tr(
                cls("depth-" + Math.min(depth, 3) + (depth == 0 ? " group" : "")),
                td(when(depth == 0 && !book.children(account).isEmpty(), () -> button(
                                type(ButtonType.BUTTON), cls("toggle"), ariaExpanded(false), ariaControls(BRANCH + account.identity()),
                                ariaLabel(message("Mostrar ou ocultar subcontas")), span(cls("chevron"), ariaHidden(true)))),
                        a(href(Screen.STATEMENT.path() + "?account=" + account.identity()),
                                text(Text.data(account.name())))),
                td(cls("hide-narrow"), span(cls("badge" + (account.financial() ? " financial" : "")),
                        text(message(Ui.typeLabel(account.type()))))),
                td(cls("num"), Ui.amount(balance, false)),
                td(cls("actions"), when(depth > 0, () -> fragment(
                        button(type(ButtonType.BUTTON), cls("link icon-button"), hxGet(path + "/edit"),
                                hxTarget("#account-editor"), hxSwap(INTO_EDITOR),
                                title(message("Editar")), ariaLabel(message("Editar " + account.name())),
                                span(cls("glyph glyph-edit"), ariaHidden(true))),
                        button(type(ButtonType.BUTTON), cls("link danger icon-button"), hxGet(path + "/delete"),
                                hxTarget("#account-editor"), hxSwap(INTO_EDITOR),
                                title(message("Excluir")), ariaLabel(message("Excluir " + account.name())),
                                span(cls("glyph glyph-delete"), ariaHidden(true))))))
        );
    }

    static Node notice(String text) {
        return p(cls("notice"), role("status"), text(message(text)));
    }

    // ---------- form

    /**
     * Creating ({@code current == null}) or editing. Changing the type reloads the form ({@code GET /accounts/form})
     * with the parents and the fields of the new type. With transactions or subaccounts the type is fixed.
     */
    static Node accountForm(Book book, Map<String, String> values, List<Violation> errors, @Nullable Account current) {
        val locked = current != null && book.fixedType(current);
        val type = current != null && locked ? current.type() : AccountFields.type(values);
        val financial = AccountFields.FINANCIAL.contains(type);
        val title = current == null ? "Nova conta" : "Editar " + current.name();
        return form(
                id("account-form"), cls("account-form card"), role("dialog"), ariaLabel(message(title)),
                method(FormMethod.POST), action(current == null ? "/accounts" : "/accounts/" + current.identity()),
                current == null ? hxPost("/accounts") : hxPut("/accounts/" + current.identity()),
                hxTarget("#accounts"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(message(title))),
                current == null ? fragment() : input(type("hidden"), name("editing"), value(current.identity().toString())),
                locked
                        ? div(cls("field"), label(text(message("Tipo"))),
                                p(cls("fixed"), text(message(Ui.typeLabel(type) + " · fixo: a conta tem lançamentos ou subcontas"))),
                                input(type("hidden"), name(AccountFields.TYPE.name()), value(type.name())))
                        : labeled(AccountFields.TYPE, "Tipo", Map.of(AccountFields.TYPE.name(), type.name()), errors,
                                types(), hxGet("/accounts/form"), hxTrigger("change"), hxTarget("#account-form"),
                                hxSwap(HxSwap.OUTER_HTML), hxInclude("closest form")),
                labeled(AccountFields.PARENT, "Conta-mãe", values, errors, parents(book, type, current)),
                labeled(AccountFields.NAME, "Nome", values, errors, null),
                when(current == null && financial, () -> labeled(AccountFields.OPENING, "Saldo inicial (R$)", values,
                        errors, null, inputmode(InputMode.DECIMAL))),
                div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("Salvar"))),
                        cancel())
        );
    }

    private static List<Option> types() {
        val options = new ArrayList<Option>();
        AccountFields.TYPES.forEach(type -> options.add(new Option(type.name(), message(Ui.typeLabel(type)))));
        return options;
    }

    private static List<Option> parents(Book book, AccountType type, @Nullable Account current) {
        return accounts(book, book.parents(type, current));
    }

    private static List<Option> accounts(Book book, List<Account> accounts) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("Escolha…")));
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
        val label = message("Excluir " + book.path(account));
        val title = h3(text(label));
        if (!book.children(account).isEmpty()) {
            return div(cls("delete-panel card"), role("dialog"), ariaLabel(label), title,
                    p(text(message("Esta conta tem subcontas: mova-as (editando a conta-mãe delas) ou exclua-as antes."))),
                    div(cls("buttons"), cancel()));
        }
        if (book.closedUses(account)) {
            return div(cls("delete-panel card"), role("dialog"), ariaLabel(label), title,
                    p(text(message("Esta conta tem lançamentos em período fechado: recue a data-limite para excluí-la."))),
                    div(cls("buttons"), cancel()));
        }
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label), hxDelete("/accounts/" + account.identity()),
                hxTarget("#accounts"),
                hxSwap(HxSwap.OUTER_HTML),
                title,
                uses > 0
                        ? fragment(p(text(message(uses + " lançamento(s) serão movidos para a conta escolhida."))),
                                labeled(AccountFields.TARGET, "Mover lançamentos para", values, errors,
                                        accounts(book, book.targets(account))))
                        : p(text(message("A conta não tem lançamentos."))),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text(message(uses > 0 ? "Mover e excluir" : "Excluir"))),
                        cancel())
        );
    }

    /** The empty book from the personal chart of accounts (INTENT F8): GET the confirmation, POST to create it. */
    static final String EMPTY = "/accounts/empty";

    /** Starting over discards everything in memory, so it is confirmed first; no file is touched. */
    static Node emptyPanel(Book book, int template) {
        val label = message("Novo livro vazio");
        return form(
                cls("delete-panel card"), role("dialog"), ariaLabel(label), method(FormMethod.POST), action(EMPTY),
                hxPost(EMPTY), hxTarget("#accounts"), hxSwap(HxSwap.OUTER_HTML),
                h3(text(label)),
                p(text(message("O livro recomeça com o plano de contas pessoal (" + template + " contas): Ativo, Passivo, "
                        + "Patrimônio líquido, Receitas e Despesas com suas categorias, e a Carteira. Bancos e cartões "
                        + "você cria depois, com o saldo inicial."))),
                p(text(message("As " + book.tree().size() + " contas e " + book.count()
                        + " lançamento(s) em memória serão descartados. Nenhum arquivo é alterado."))),
                div(cls("buttons"),
                        button(type(ButtonType.SUBMIT), cls("danger"), text(message("Criar livro vazio"))),
                        cancel())
        );
    }

    private static Node cancel() {
        return button(type(ButtonType.BUTTON), cls("link"), hxGet("/accounts/editor"), hxTarget("#account-editor"),
                hxSwap(HxSwap.INNER_HTML), text(message("Cancelar")));
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("account", field, label, values, errors, options, AccountFields::message, extra);
    }
}
