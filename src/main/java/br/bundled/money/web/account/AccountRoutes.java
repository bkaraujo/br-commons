package br.bundled.money.web.account;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._1_application.AccountInput;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.ValidationRule;
import br.commons.lang.html.form.Violation;
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

import java.time.YearMonth;
import java.util.*;

import static br.bundled.money.web.ui.Ui.ok;
import static br.bundled.money.web.ui.Ui.pathId;

/**
 * The chart of accounts and its register. {@code GET /accounts}: the tree. The editor
 * ({@code #account-editor}) receives the form ({@code /accounts/new}, {@code /accounts/{id}/edit}, re-rendered by
 * {@code /accounts/form} when the type changes) or the delete panel ({@code /accounts/{id}/delete}). Saving and
 * deleting answer with the whole {@code #accounts}; invalid, the form or the panel with its errors; refused by the
 * services (a rule of the domain), the tree with the reason. {@code /accounts/empty}: the empty book from the personal
 * chart of accounts (INTENT F8), confirmed first. Beside the tree, the month of an account
 * ({@code /accounts?account=3&month=2026-10}, only {@code #account-panel} when the tree or the month navigation
 * asks); without one, the first financial account. Literal routes come before {@code {id}}: the first match wins.
 */
@NullMarked
public final class AccountRoutes implements WebResource {

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get("/accounts", MediaType.TEXT_HTML, String.class, this::tree).getOrThrow();
        router.get("/accounts/new", MediaType.TEXT_HTML, String.class, this::blank).getOrThrow();
        router.get("/accounts/form", MediaType.TEXT_HTML, String.class, this::reform).getOrThrow();
        router.get("/accounts/editor", MediaType.TEXT_HTML, String.class, _ -> ok("")).getOrThrow();
        router.get(RegisterViews.EMPTY, MediaType.TEXT_HTML, String.class, this::confirmEmpty).getOrThrow();
        router.post(RegisterViews.EMPTY, MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::empty)
                .getOrThrow();
        router.post("/accounts", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/accounts/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/accounts/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        // a form without htmx cannot send PUT: it posts to the same path
        router.post("/accounts/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.get("/accounts/{id}/delete", MediaType.TEXT_HTML, String.class, this::confirm).getOrThrow();
        router.delete("/accounts/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse tree(WebRequest request) {
        return Ui.partial(request) ? ok(RegisterViews.panel(book, chosen(request), month(request)).render())
                : page(request, Html.fragment());
    }

    /** The account asked, else the first financial one, else the first of all. */
    private Account chosen(WebRequest request) {
        return book.account(Ui.uuid(request.parameter("account"))).orElseGet(() -> {
            val tree = book.tree();
            return tree.stream().filter(Account::financial).findFirst().orElse(tree.getFirst());
        });
    }

    private YearMonth month(WebRequest request) {
        return Ui.monthOf(request.parameter(Ledger.MONTH), book::month);
    }

    // ---------- editor

    private HTTPResponse blank(WebRequest request) {
        return editor(request, RegisterViews.accountForm(book, Map.of(), List.of(), null));
    }

    /** The form again with the submitted values: the type changed, so do the parents and the fields. */
    private HTTPResponse reform(WebRequest request) {
        val values = AccountFields.values(name -> Objects.requireNonNullElse(request.parameter(name), ""));
        val editing = book.account(Ui.uuid(request.parameter("editing"))).orElse(null);
        return ok(RegisterViews.accountForm(book, values, List.of(), editing).render());
    }

    private HTTPResponse edit(WebRequest request) {
        return below(request)
                .map(account -> editor(request, RegisterViews.accountForm(book, AccountFields.values(account), List.of(), account)))
                .orElseGet(() -> ok(""));
    }

    private HTTPResponse confirm(WebRequest request) {
        return below(request)
                .map(account -> editor(request, RegisterViews.deletePanel(book, account, Map.of(), List.of())))
                .orElseGet(() -> ok(""));
    }

    /** The account of the path, below the top level (the top level is fixed). */
    private Optional<Account> below(WebRequest request) {
        return book.account(pathId(request)).filter(account -> account.parent() != null);
    }

    /** htmx: just the editor's content. A plain GET: the page with the editor open. */
    private HTTPResponse editor(WebRequest request, Node content) {
        return Ui.partial(request) ? ok(content.render()) : page(request, content);
    }

    // ---------- empty book (INTENT F8)

    private HTTPResponse confirmEmpty(WebRequest request) {
        return editor(request, RegisterViews.emptyPanel(book, ChartTemplate.personal().size()));
    }

    /** The book starts over from the template; without htmx: post/redirect/get. */
    private HTTPResponse empty(WebRequest request) {
        val started = book.startEmpty();
        if (!HtmxRequest.of(request).isHtmx()) return redirected(request, started);
        return saved(started, "Livro vazio criado com o plano de contas pessoal.");
    }

    // ---------- create, update, delete

    /** Valid: the tree with the new account and a notice. Invalid: the form with its errors (htmx retargets it). */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (AccountFields.read(form::value, book, null)) {
            case Result.Success<AccountInput, List<Violation>>(var input) -> {
                val created = book.create(input);
                yield htmx ? saved(created, created.map(account -> "Conta \"" + book.path(account) + "\" criada.").getOrElse(""))
                        : redirected(request, created);
            }
            case Result.Failure<AccountInput, List<Violation>>(var errors) -> {
                val view = RegisterViews.accountForm(book, AccountFields.values(form::value), errors, null);
                yield htmx ? invalid(view, "#account-form") : page(request, view);
            }
        };
    }

    /** Like {@link #create}, over the account of the path; without htmx: post/redirect/get, or the page with the errors. */
    private HTTPResponse update(WebRequest request) {
        val htmx = HtmxRequest.of(request).isHtmx();
        val current = below(request);
        if (current.isEmpty()) {
            return htmx ? ok("") : WebResponse.redirect("/accounts");
        }
        val form = request.body(Form.class).getOrThrow();
        return switch (AccountFields.read(form::value, book, current.get())) {
            case Result.Success<AccountInput, List<Violation>>(var input) -> {
                val updated = book.update(current.get(), input);
                yield htmx ? saved(updated, updated.map(account -> "Conta \"" + book.path(account) + "\" atualizada.").getOrElse(""))
                        : redirected(request, updated);
            }
            case Result.Failure<AccountInput, List<Violation>>(var errors) -> {
                val view = RegisterViews.accountForm(book, AccountFields.values(form::value), errors, current.get());
                yield htmx ? invalid(view, "#account-form") : page(request, view);
            }
        };
    }

    /**
     * With transactions, a target is required (one of {@link Book#targets}); with subaccounts nothing is deleted.
     * htmx sends the panel's fields of a DELETE in the query string.
     */
    private HTTPResponse delete(WebRequest request) {
        val found = below(request);
        if (found.isEmpty()) {
            return ok("");
        }
        val account = found.get();
        if (!book.children(account).isEmpty() || book.closedUses(account)) {
            return invalid(RegisterViews.deletePanel(book, account, Map.of(), List.of()), "#account-editor");
        }
        val uses = book.uses(account);
        val target = Ui.uuid(request.parameter(AccountFields.TARGET.name()));
        val destination = destination(account, target);
        if (uses > 0 && destination == null) {
            return invalid(RegisterViews.deletePanel(book, account, Map.of(), List.of(targetError(target))), "#account-editor");
        }
        val path = book.path(account);
        val moveTo = uses > 0 ? destination : null;
        return saved(book.remove(account, moveTo), removed(path, uses, moveTo));
    }

    private String removed(String path, long uses, @Nullable Account moveTo) {
        return moveTo == null ? "Conta \"" + path + "\" excluída."
                : "Conta \"" + path + "\" excluída; " + uses + " lançamento(s) movidos para \"" + book.path(moveTo) + "\".";
    }

    /** The chosen target, if it is one of {@link Book#targets}. */
    private @Nullable Account destination(Account account, @Nullable UUID target) {
        if (target == null) return null;
        return book.targets(account).stream().filter(candidate -> candidate.identity().equals(target)).findFirst().orElse(null);
    }

    private static Violation targetError(@Nullable UUID target) {
        return new Violation(AccountFields.TARGET.name(), target == null ? ValidationRule.REQUIRED : ValidationRule.INVALID);
    }

    /** The tree with {@code notice}, or with the reason the services gave for refusing. */
    private HTTPResponse saved(Result<?, BusinessError> result, String notice) {
        val text = result instanceof Result.Failure<?, BusinessError>(var error) ? error.render() : notice;
        return ok(RegisterViews.screen(book, RegisterViews.notice(text)).render());
    }

    /** Without htmx: post/redirect/get when the service did it; the page with the reason (422) when it refused. */
    private HTTPResponse redirected(WebRequest request, Result<?, BusinessError> result) {
        return result instanceof Result.Failure<?, BusinessError>(var error)
                ? Layout.page(request, HTTPStatus.UNPROCESSABLE_CONTENT, Screen.ACCOUNTS,
                        Ledger.monthNav(book, month(request)), RegisterViews.layout(book, RegisterViews.notice(error.render()), chosen(request), month(request)))
                : WebResponse.redirect("/accounts");
    }

    private static HTTPResponse invalid(Node view, String target) {
        val swap = "#account-form".equals(target) ? HxSwap.OUTER_HTML : HxSwap.INNER_HTML;
        return HtmxResponse.of(ok(view.render())).retarget(target).reswap(swap).response();
    }

    private HTTPResponse page(WebRequest request, Node editor) {
        return Layout.page(request, Screen.ACCOUNTS, Ledger.monthNav(book, month(request)),
                RegisterViews.layout(book, editor, chosen(request), month(request)));
    }
}
