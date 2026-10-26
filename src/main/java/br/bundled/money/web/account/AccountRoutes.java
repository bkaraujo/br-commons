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
import br.commons.locale.i18n.Text;
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
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

import static br.bundled.money.web.ui.Ui.ok;
import static br.bundled.money.web.ui.Ui.pathId;

/**
 * The chart of accounts and its register. {@code GET /accounts}: the tree. The editor
 * ({@code #account-editor}) receives the form ({@code /accounts/new}, {@code /accounts/{id}/edit}, re-rendered by
 * {@code /accounts/form} when the type changes) or the delete panel ({@code /accounts/{id}/delete}). Saving and
 * deleting answer with the whole {@code #accounts} and, out of band, the month beside it and the month navigation;
 * invalid, the form or the panel with its errors; refused by the services (a rule of the domain), the tree with the
 * reason; over an account already gone, the tree with a notice.
 * {@code /accounts/empty}: the empty book from the personal chart of accounts (INTENT F8), confirmed first.
 * {@code /accounts/closing}: the closing month (INTENT F9) in the editor; moving it back is confirmed first. Beside the
 * tree, the month of an account ({@code /accounts?account=3&month=2026-10}, only {@code #account-panel} when the tree
 * or the month navigation asks); without one, the first financial account. Literal routes come before {@code {id}}:
 * the first match wins.
 */
@NullMarked
public final class AccountRoutes implements WebResource {

    /** The closing date (INTENT F9): GET the dialog, POST to set it; the other screens link here. */
    public static final String CLOSING = "/accounts/closing";

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
        router.get(CLOSING, MediaType.TEXT_HTML, String.class, this::closing).getOrThrow();
        router.post(CLOSING, MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::close).getOrThrow();
        router.post("/accounts", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/accounts/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/accounts/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        // a form without htmx cannot send PUT: it posts to the same path
        router.post("/accounts/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.get("/accounts/{id}/delete", MediaType.TEXT_HTML, String.class, this::confirm).getOrThrow();
        router.delete("/accounts/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse tree(WebRequest request) {
        val account = chosen(request::parameter);
        val month = month(request::parameter);
        if (Ui.partial(request) || Ui.monthOnly(request)) {
            val panel = RegisterViews.panel(book, account, month, false);
            return Ui.partial(request) ? ok(panel.render())
                    : Layout.region(request, Ledger.monthNav(book, month), "#" + RegisterViews.PANEL, panel);
        }
        return page(request, Html.fragment());
    }

    /** The account asked, else the first financial one, else the first of all. */
    private Account chosen(Function<String, @Nullable String> value) {
        return book.account(Ui.uuid(value.apply("account"))).orElseGet(() -> {
            val tree = book.tree();
            return tree.stream().filter(Account::financial).findFirst().orElse(tree.getFirst());
        });
    }

    private YearMonth month(Function<String, @Nullable String> value) {
        return Ui.monthOf(value.apply(Ledger.MONTH), book::month);
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
        return saved(request, started, Ui.message("forms.account.empty_created"));
    }

    // ---------- closing (INTENT F9)

    private HTTPResponse closing(WebRequest request) {
        return editor(request, RegisterViews.closingPanel(book, null, null));
    }

    /**
     * Closes up to the end of the month asked ({@link RegisterViews#threshold}); empty, or "Remover fechamento", nothing
     * is closed. Moving it back (or removing it) reopens transactions, so it asks for the confirmation first. Done: the
     * tree without a notice (the button shows the month), or with the reason it was refused; without htmx:
     * post/redirect/get.
     */
    private HTTPResponse close(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val raw = form.value(RegisterViews.REMOVE).isEmpty() ? form.value(RegisterViews.MONTH).strip() : "";
        final YearMonth month;
        try {
            month = raw.isEmpty() ? null : YearMonth.parse(raw);
        } catch (DateTimeParseException _) {
            return dialog(request, RegisterViews.closingPanel(book, null, Ui.message("forms.closing.invalid_month")), true);
        }
        val date = month == null ? null : RegisterViews.threshold(month);
        val current = book.threshold();
        val reopens = current != null && (date == null || date.isBefore(current));
        if (reopens && form.value(RegisterViews.CONFIRM).isEmpty()) {
            return dialog(request, RegisterViews.closingPanel(book, raw, null), false);
        }
        val closed = book.close(date);
        if (!HtmxRequest.of(request).isHtmx()) return redirected(request, closed);
        return closed instanceof Result.Failure<?, BusinessError>(var error)
                ? withNotice(request, Text.message(error.render())) : refreshed(request, Html.fragment());
    }

    /** The closing dialog again: into the editor for htmx; without, on the page ({@code refused}: 422). */
    private HTTPResponse dialog(WebRequest request, Node panel, boolean refused) {
        return HtmxRequest.of(request).isHtmx() ? invalid(panel, "#account-editor")
                : page(request, refused ? HTTPStatus.UNPROCESSABLE_CONTENT : HTTPStatus.OK, panel);
    }

    // ---------- create, update, delete

    /** Valid: the tree with the new account and a notice. Invalid: the form with its errors (htmx retargets it). */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (AccountFields.read(form::value, book, null)) {
            case Result.Success<AccountInput, List<Violation>>(var value) -> {
                val input = Objects.requireNonNull(value);
                val created = book.create(input);
                yield htmx ? saved(request, created, created.map(account -> Ui.message("forms.account.created", book.path(account))).getOrElse(Text.data("")))
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
            return htmx ? withNotice(request, gone()) : WebResponse.redirect("/accounts");
        }
        val form = request.body(Form.class).getOrThrow();
        return switch (AccountFields.read(form::value, book, current.get())) {
            case Result.Success<AccountInput, List<Violation>>(var value) -> {
                val input = Objects.requireNonNull(value);
                val updated = book.update(current.get(), input);
                yield htmx ? saved(request, updated, updated.map(account -> Ui.message("forms.account.updated", book.path(account))).getOrElse(Text.data("")))
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
            return withNotice(request, gone());
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
        return saved(request, book.remove(account, moveTo), removed(path, uses, moveTo));
    }

    private Text removed(String path, long uses, @Nullable Account moveTo) {
        return moveTo == null ? Ui.message("forms.account.removed", path)
                : Ui.message("forms.account.removed_moved", path, uses, book.path(moveTo));
    }

    /** Saving or deleting an account deleted meanwhile (another tab): the tree comes back with it. */
    private static Text gone() {
        return Ui.message("forms.account.gone");
    }

    /** The chosen target, if it is one of {@link Book#targets}. */
    private @Nullable Account destination(Account account, @Nullable UUID target) {
        if (target == null) return null;
        return book.targets(account).stream().filter(candidate -> candidate.identity().equals(target)).findFirst().orElse(null);
    }

    private static Violation targetError(@Nullable UUID target) {
        return new Violation(AccountFields.TARGET.name(), target == null ? ValidationRule.REQUIRED : ValidationRule.INVALID);
    }

    /**
     * The tree with {@code notice} (or with the reason the services gave for refusing) and, out of band, the month
     * beside it: of the page's account and month ({@code HX-Current-URL}), or of the first financial account when that
     * one is gone.
     */
    private HTTPResponse saved(WebRequest request, Result<?, BusinessError> result, Text notice) {
        return withNotice(request, result instanceof Result.Failure<?, BusinessError>(var error) ? Text.message(error.render()) : notice);
    }

    /**
     * The tree with {@code text} and, out of band, the panel and the month navigation of the same month: without
     * {@code month=} in the URL it is the book's, which the write may have moved.
     */
    private HTTPResponse withNotice(WebRequest request, Text text) {
        return refreshed(request, RegisterViews.notice(text));
    }

    /** The tree with {@code editor} and, out of band, the panel and the month navigation ({@link #withNotice}). */
    private HTTPResponse refreshed(WebRequest request, Node editor) {
        val account = chosen(name -> Ui.shown(request, name));
        val month = month(name -> Ui.shown(request, name));
        return ok(Html.fragment(RegisterViews.screen(book, editor, account),
                RegisterViews.panel(book, account, month, true),
                Layout.monthChanged(request, Ledger.monthNav(book, month))).render());
    }

    /** Without htmx: post/redirect/get when the service did it; the page with the reason (422) when it refused. */
    private HTTPResponse redirected(WebRequest request, Result<?, BusinessError> result) {
        return result instanceof Result.Failure<?, BusinessError>(var error)
                ? Layout.page(request, HTTPStatus.UNPROCESSABLE_CONTENT, Screen.ACCOUNTS,
                        Ledger.monthNav(book, month(request::parameter)), RegisterViews.layout(book,
                                RegisterViews.notice(Text.message(error.render())), chosen(request::parameter), month(request::parameter)))
                : WebResponse.redirect("/accounts");
    }

    private static HTTPResponse invalid(Node view, String target) {
        val swap = "#account-form".equals(target) ? HxSwap.OUTER_HTML : HxSwap.INNER_HTML;
        return HtmxResponse.of(ok(view.render())).retarget(target).reswap(swap).response();
    }

    private HTTPResponse page(WebRequest request, Node editor) {
        return page(request, HTTPStatus.OK, editor);
    }

    private HTTPResponse page(WebRequest request, HTTPStatus status, Node editor) {
        val month = month(request::parameter);
        return Layout.page(request, status, Screen.ACCOUNTS, Ledger.monthNav(book, month),
                RegisterViews.layout(book, editor, chosen(request::parameter), month));
    }
}
