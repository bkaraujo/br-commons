package br.bundled.money.web.account;

import br.bundled.money.web.mock.AccountInput;
import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.mock.Seed;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Violation;
import br.commons.lang.html.form.ValidationRule;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.bundled.money.web.ui.Ui.ok;
import static br.bundled.money.web.ui.Ui.pathId;

/**
 * The chart of accounts, its register and the ledger. {@code GET /accounts}: the tree. The editor
 * ({@code #account-editor}) receives the form ({@code /accounts/new}, {@code /accounts/{id}/edit}, re-rendered by
 * {@code /accounts/form} when the type changes) or the delete panel ({@code /accounts/{id}/delete}). Saving and
 * deleting answer with the whole {@code #accounts}; invalid, the form or the panel with its errors.
 * {@code /accounts/empty}: the empty book from the personal chart of accounts (INTENT F8), confirmed first.
 * {@code GET /accounts/{id}?month=2026-10}: the ledger. Literal routes come before {@code {id}}: the first match wins.
 */
@NullMarked
public final class AccountRoutes implements WebResource {

    private final Book book;

    public AccountRoutes(Book book) {
        this.book = book;
    }

    @Override
    public void routes(Router router) {
        router.get("/accounts", MediaType.TEXT_HTML, String.class, this::tree).getOrThrow();
        router.get("/accounts/new", MediaType.TEXT_HTML, String.class, this::blank).getOrThrow();
        router.get("/accounts/form", MediaType.TEXT_HTML, String.class, this::reform).getOrThrow();
        router.get("/accounts/editor", MediaType.TEXT_HTML, String.class, request -> ok("")).getOrThrow();
        router.get(RegisterViews.EMPTY, MediaType.TEXT_HTML, String.class, this::confirmEmpty).getOrThrow();
        router.post(RegisterViews.EMPTY, MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::empty)
                .getOrThrow();
        router.post("/accounts", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/accounts/{id}", MediaType.TEXT_HTML, String.class, this::ledger).getOrThrow();
        router.get("/accounts/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/accounts/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.get("/accounts/{id}/delete", MediaType.TEXT_HTML, String.class, this::confirm).getOrThrow();
        router.delete("/accounts/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse tree(WebRequest request) {
        return Ui.partial(request) ? ok(RegisterViews.screen(book, Html.fragment()).render())
                : page(request, Html.fragment());
    }

    // ---------- editor

    private HTTPResponse blank(WebRequest request) {
        return editor(request, RegisterViews.accountForm(book, Map.of(), List.of(), null));
    }

    /** The form again with the submitted values: the type changed, so do the parents and the fields. */
    private HTTPResponse reform(WebRequest request) {
        val values = AccountFields.values(name -> Objects.requireNonNullElse(request.parameter(name), ""));
        val editing = book.account(Objects.requireNonNullElse(Ui.number(request.parameter("editing")), -1)).orElse(null);
        return ok(RegisterViews.accountForm(book, values, List.of(), editing).render());
    }

    private HTTPResponse edit(WebRequest request) {
        return book.account(pathId(request)).filter(account -> account.parent() != null)
                .map(account -> editor(request, RegisterViews.accountForm(book, AccountFields.values(account), List.of(), account)))
                .orElseGet(() -> ok(""));
    }

    private HTTPResponse confirm(WebRequest request) {
        return book.account(pathId(request)).filter(account -> account.parent() != null)
                .map(account -> editor(request, RegisterViews.deletePanel(book, account, Map.of(), List.of())))
                .orElseGet(() -> ok(""));
    }

    /** htmx: just the editor's content. A plain GET: the page with the editor open. */
    private HTTPResponse editor(WebRequest request, Node content) {
        return Ui.partial(request) ? ok(content.render()) : page(request, content);
    }

    // ---------- empty book (INTENT F8)

    private HTTPResponse confirmEmpty(WebRequest request) {
        return editor(request, RegisterViews.emptyPanel(book, Seed.template().size()));
    }

    /** The book starts over from the template; without htmx: post/redirect/get. */
    private HTTPResponse empty(WebRequest request) {
        book.reset(Seed.template());
        return HtmxRequest.of(request).isHtmx() ? saved("Livro vazio criado com o plano de contas pessoal.")
                : WebResponse.redirect("/accounts");
    }

    // ---------- create, update, delete

    /** Valid: the tree with the new account and a notice. Invalid: the form with its errors (htmx retargets it). */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (AccountFields.read(form::value, book, null)) {
            case Result.Success<AccountInput, List<Violation>>(var input) -> {
                val account = book.create(input);
                yield htmx ? saved("Conta \"" + book.path(account) + "\" criada.") : WebResponse.redirect("/accounts");
            }
            case Result.Failure<AccountInput, List<Violation>>(var errors) -> {
                val view = RegisterViews.accountForm(book, AccountFields.values(form::value), errors, null);
                yield htmx ? invalid(view, "#account-form") : page(request, view);
            }
        };
    }

    private HTTPResponse update(WebRequest request) {
        val current = book.account(pathId(request)).filter(account -> account.parent() != null);
        if (current.isEmpty()) {
            return ok("");
        }
        val form = request.body(Form.class).getOrThrow();
        return switch (AccountFields.read(form::value, book, current.get())) {
            case Result.Success<AccountInput, List<Violation>>(var input) -> {
                yield book.update(current.get().id(), input)
                        .map(account -> saved("Conta \"" + book.path(account) + "\" atualizada."))
                        .orElseGet(() -> saved("A conta não existe mais."));
            }
            case Result.Failure<AccountInput, List<Violation>>(var errors) -> invalid(
                    RegisterViews.accountForm(book, AccountFields.values(form::value), errors, current.get()), "#account-form");
        };
    }

    /**
     * With transactions, a target is required (one of {@link Book#targets}); with subaccounts nothing is deleted.
     * htmx sends the panel's fields of a DELETE in the query string.
     */
    private HTTPResponse delete(WebRequest request) {
        val found = book.account(pathId(request)).filter(account -> account.parent() != null);
        if (found.isEmpty()) {
            return ok("");
        }
        val account = found.get();
        if (!book.children(account.id()).isEmpty() || book.closedUses(account.id())) {
            return invalid(RegisterViews.deletePanel(book, account, Map.of(), List.of()), "#account-editor");
        }
        val uses = book.uses(account.id());
        val target = Ui.number(request.parameter(AccountFields.TARGET.name()));
        val destination = destination(account, target);
        if (uses > 0 && destination == null) {
            return invalid(RegisterViews.deletePanel(book, account, Map.of(), List.of(targetError(target))), "#account-editor");
        }
        val path = book.path(account);
        val moveTo = uses > 0 ? destination : null;
        book.remove(account.id(), moveTo == null ? null : moveTo.id());
        return saved(removed(path, uses, moveTo));
    }

    private String removed(String path, long uses, @Nullable MockAccount moveTo) {
        return moveTo == null ? "Conta \"" + path + "\" excluída."
                : "Conta \"" + path + "\" excluída; " + uses + " lançamento(s) movidos para \"" + book.path(moveTo) + "\".";
    }

    /** The chosen target, if it is one of {@link Book#targets}. */
    private @Nullable MockAccount destination(MockAccount account, @Nullable Integer target) {
        if (target == null) return null;
        return book.targets(account.id()).stream().filter(candidate -> candidate.id() == target).findFirst().orElse(null);
    }

    private static Violation targetError(@Nullable Integer target) {
        return new Violation(AccountFields.TARGET.name(), target == null ? ValidationRule.REQUIRED : ValidationRule.INVALID);
    }

    private HTTPResponse saved(String notice) {
        return ok(RegisterViews.screen(book, RegisterViews.notice(notice)).render());
    }

    private static HTTPResponse invalid(Node view, String target) {
        val swap = "#account-form".equals(target) ? HxSwap.OUTER_HTML : HxSwap.INNER_HTML;
        return HtmxResponse.of(ok(view.render())).retarget(target).reswap(swap).response();
    }

    private HTTPResponse page(WebRequest request, Node editor) {
        return Layout.page(request, Screen.ACCOUNTS, RegisterViews.screen(book, editor));
    }

    // ---------- ledger

    private HTTPResponse ledger(WebRequest request) {
        val account = book.account(pathId(request));
        if (account.isEmpty()) {
            return Layout.page(request, HTTPStatus.NOT_FOUND, Screen.ACCOUNTS, AccountViews.missing());
        }
        val month = Ui.monthOf(request.parameter("month"), book.month());
        val view = AccountViews.ledger(book, account.get(), month);
        val screen = account.get().financial() ? Screen.HOME : Screen.ACCOUNTS;
        return Ui.partial(request) ? ok(view.render()) : Layout.page(request, screen, view);
    }
}
