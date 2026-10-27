package br.bundled.money.web.views.statement;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Ledger;
import br.bundled.money.web.views.Layout;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Ui;
import br.bundled.money.web.views.tools.Validators;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static br.bundled.money.web.views.Ui.ok;
import static br.bundled.money.web.views.tools.Validators.pathId;

/**
 * {@code GET /statement?account=3&month=2026-10}: the whole page, or only {@code #statement-panel} when the list asks
 * (the month navigation swaps the content: the list's values change with the month). Without a valid account, the
 * first financial one. The editor
 * ({@code #statement-editor}) receives the form ({@code /statement/transactions/new},
 * {@code /statement/transactions/{id}/edit}) or the confirmation ({@code /statement/transactions/{id}/delete});
 * creating, saving and deleting answer with the panel of the account and month sent along, plus the list out of
 * band; invalid, the form with its errors. The simple form or the form of splits (marked by {@link SplitFields#MODE}),
 * re-rendered by {@code /statement/transactions/split} as lines are added or removed. Literal routes come before
 * {@code {id}}. {@code GET /statement/transactions/{id}?account=…&month=…}: the detail of a transaction (its note
 * shows only there), a page reached from the description of a line ({@link Ledger#described}).
 */
@NullMarked
public final class StatementRoutes implements WebResource {

    /** The transaction is not there anymore (deleted meanwhile, or a wrong id). */
    private static final String GONE = "forms.statement.gone";

    private final Book book = Context.get(Book.class);

    @Override
    public List<Result<Route, String>> routes() {
        return List.of(
                Route.get("/statement", MediaType.TEXT_HTML, String.class, true, this::statement),
                Route.get("/statement/editor", MediaType.TEXT_HTML, String.class, true, _ -> ok("")),
                Route.get("/statement/transactions/new", MediaType.TEXT_HTML, String.class, true, this::blank),
                Route.get(StatementViews.SPLIT, MediaType.TEXT_HTML, String.class, true, this::split),
                Route.post("/statement/transactions", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, true, this::create),
                Route.get("/statement/transactions/{id}/edit", MediaType.TEXT_HTML, String.class, true, this::edit),
                Route.get("/statement/transactions/{id}", MediaType.TEXT_HTML, String.class, true, this::detail),
                Route.put("/statement/transactions/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, true, this::update),
                Route.get("/statement/transactions/{id}/delete", MediaType.TEXT_HTML, String.class, true, this::confirm),
                Route.delete("/statement/transactions/{id}", MediaType.TEXT_HTML, String.class, true, this::delete));
    }

    private HTTPResponse statement(WebRequest request) {
        val month = month(request::parameter);
        val account = chosen(request::parameter);
        return Ui.partial(request) ? ok(StatementViews.panel(book, account, month, Html.fragment()).render())
                : Layout.page(request, Screen.STATEMENT, Ledger.monthNav(book, month),
                        StatementViews.screen(book, account, month, Html.fragment()));
    }

    // ---------- editor

    /** Dated today when the month is the current one; on its first day otherwise. */
    private HTTPResponse blank(WebRequest request) {
        val account = chosen(request::parameter);
        val month = month(request::parameter);
        val date = YearMonth.from(book.today()).equals(month) ? book.today() : month.atDay(1);
        return ok(StatementViews.transactionForm(book, null, StatementFields.blank(date, account), List.of(), account,
                month).render());
    }

    /**
     * The transaction in full, seen from the account and month sent along: with htmx, only the dialog
     * ({@link Layout#ENTRY_MODAL}); gone: a 404 with the way back (in the dialog, a 200: htmx does not swap a 404).
     */
    private HTTPResponse detail(WebRequest request) {
        val account = chosen(request::parameter);
        val month = month(request::parameter);
        if (Ui.partial(request)) {
            return ok(book.transaction(pathId(request))
                    .map(transaction -> StatementViews.detail(book, transaction, account, month, true))
                    .orElseGet(() -> StatementViews.gone(account, month)).render());
        }
        return book.transaction(pathId(request))
                .map(transaction -> Layout.page(request, Screen.STATEMENT, StatementViews.detail(book, transaction, account, month, false)))
                .orElseGet(() -> Layout.page(request, HTTPStatus.NOT_FOUND, Screen.STATEMENT, StatementViews.gone(account, month)));
    }

    /** A simple transaction in the simple form; any other in the form of splits. */
    private HTTPResponse edit(WebRequest request) {
        val account = chosen(request::parameter);
        val month = month(request::parameter);
        return book.transaction(pathId(request)).filter(transaction -> !book.closed(transaction))
                .map(transaction -> {
                    val values = StatementFields.values(book, transaction);
                    return ok((book.simple(transaction)
                            ? StatementViews.transactionForm(book, transaction, values, List.of(), account, month)
                            : StatementViews.splitForm(book, transaction, values, SplitFields.lines(transaction),
                                    List.of(), account, month)).render());
                })
                .orElseGet(() -> ok(""));
    }

    /**
     * The form of splits from the values sent along (htmx sends the form in the query string): the lines typed, or
     * the simple form turned into lines; {@code add} appends a blank line, {@code remove} drops one (two stay).
     */
    private HTTPResponse split(WebRequest request) {
        val form = Form.parse(Objects.requireNonNullElse(request.uri().getRawQuery(), ""));
        val values = StatementFields.values(form::value);
        val typed = SplitFields.SPLITS.equals(form.value(SplitFields.MODE));
        val lines = new ArrayList<>(typed ? SplitFields.lines(form::all) : SplitFields.lines(values));
        val remove = Validators.number(form.value("remove"));
        if (remove != null && remove < lines.size() && lines.size() > 2) lines.remove((int) remove);
        if (!form.value("add").isEmpty() || lines.size() < 2) lines.add(SplitFields.Line.BLANK);
        val transaction = editing(form::value).orElse(null);
        return ok(StatementViews.splitForm(book, transaction, values, lines, List.of(), chosen(form::value),
                month(form::value)).render());
    }

    /** The transaction being edited ({@link StatementFields#EDITING}), while it can still be changed. */
    private Optional<Transaction> editing(Function<String, @Nullable String> value) {
        return book.transaction(Validators.uuid(value.apply(StatementFields.EDITING))).filter(transaction -> !book.closed(transaction));
    }

    private HTTPResponse confirm(WebRequest request) {
        return book.transaction(pathId(request)).filter(transaction -> !book.closed(transaction))
                .map(transaction -> ok(StatementViews.deletePanel(book, transaction, chosen(request::parameter),
                        month(request::parameter)).render()))
                .orElseGet(() -> ok(""));
    }

    // ---------- update, delete

    /** Without htmx: post/redirect/get to the statement of the account and month ({@link #after}). */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val account = chosen(form::value);
        val month = month(form::value);
        if (SplitFields.SPLITS.equals(form.value(SplitFields.MODE))) {
            val lines = SplitFields.lines(form::all);
            return switch (SplitFields.read(form::value, lines, book, null)) {
                case Result.Success<SplitFields.Entry, List<Violation>>(var value) -> {
                    val entry = Objects.requireNonNull(value);
                    val posted = book.enter(entry.date(), entry.description(), entry.splits(), entry.note());
                    yield after(request, account, month, posted, Ui.message("forms.statement.created", entry.description()));
                }
                case Result.Failure<SplitFields.Entry, List<Violation>>(var errors) -> invalid(StatementViews.splitForm(
                        book, null, StatementFields.values(form::value), lines, errors, account, month));
            };
        }
        return switch (StatementFields.read(form::value, book, null)) {
            case Result.Success<StatementFields.Revision, List<Violation>>(var value) -> {
                val revision = Objects.requireNonNull(value);
                val posted = book.move(revision.date(), revision.description(), revision.from(), revision.to(),
                        revision.value(), revision.note());
                yield after(request, account, month, posted, Ui.message("forms.statement.created", revision.description()));
            }
            case Result.Failure<StatementFields.Revision, List<Violation>>(var errors) -> invalid(
                    StatementViews.transactionForm(book, null, StatementFields.values(form::value), errors, account, month));
        };
    }

    private HTTPResponse update(WebRequest request) {
        val found = book.transaction(pathId(request));
        val form = request.body(Form.class).getOrThrow();
        val account = chosen(form::value);
        val month = month(form::value);
        if (found.isEmpty()) {
            return changed(account, month, Ui.message(GONE));
        }
        if (book.closed(found.get())) {
            return changed(account, month, StatementViews.closedNotice());
        }
        if (SplitFields.SPLITS.equals(form.value(SplitFields.MODE))) {
            val lines = SplitFields.lines(form::all);
            return switch (SplitFields.read(form::value, lines, book, found.get())) {
                case Result.Success<SplitFields.Entry, List<Violation>>(var value) -> {
                    val entry = Objects.requireNonNull(value);
                    val revised = book.rewrite(found.get(), entry.date(), entry.description(), entry.splits(), entry.note());
                    yield changed(account, month, outcome(revised, Ui.message("forms.statement.updated",
                            named(entry.description(), found.get()))));
                }
                case Result.Failure<SplitFields.Entry, List<Violation>>(var errors) -> invalid(StatementViews.splitForm(
                        book, found.get(), StatementFields.values(form::value), lines, errors, account, month));
            };
        }
        if (!book.simple(found.get())) {
            return changed(account, month, Ui.message("forms.statement.not_simple"));
        }
        return switch (StatementFields.read(form::value, book, found.get())) {
            case Result.Success<StatementFields.Revision, List<Violation>>(var value) -> {
                val revision = Objects.requireNonNull(value);
                val revised = book.revise(found.get(), revision.date(), revision.description(), revision.from(),
                        revision.to(), revision.value(), revision.note());
                yield changed(account, month, outcome(revised, Ui.message("forms.statement.updated",
                        named(revision.description(), found.get()))));
            }
            case Result.Failure<StatementFields.Revision, List<Violation>>(var errors) -> invalid(
                    StatementViews.transactionForm(book, found.get(), StatementFields.values(form::value), errors,
                            account, month));
        };
    }

    private static HTTPResponse invalid(Node form) {
        return HtmxResponse.of(ok(form.render())).retarget("#transaction-form").reswap(HxSwap.OUTER_HTML).response();
    }

    /** htmx sends the confirmation's fields of a DELETE in the query string. */
    private HTTPResponse delete(WebRequest request) {
        val account = chosen(request::parameter);
        val month = month(request::parameter);
        val found = book.transaction(pathId(request));
        if (found.isPresent() && book.closed(found.get())) {
            return changed(account, month, StatementViews.closedNotice());
        }
        return changed(account, month, found.map(transaction -> outcome(book.discard(transaction),
                Ui.message("forms.statement.deleted", transaction.label()))).orElseGet(() -> Ui.message(GONE)));
    }

    /** The name of an edited transaction: the {@code description} given or, when blank, its PDF name. */
    private static String named(String description, Transaction transaction) {
        val pdfName = transaction.pdfName();
        return description.isBlank() && pdfName != null ? pdfName : description;
    }

    /** {@code done} when the service did it; otherwise the reason it gave (a rule of the domain). */
    private static Text outcome(Result<?, BusinessError> result, Text done) {
        return result instanceof Result.Failure<?, BusinessError>(var error) ? Text.message(error.render()) : done;
    }

    /**
     * After a change: with htmx, the panel with the outcome ({@link #changed}); without, post/redirect/get to the
     * statement when the service did it, or the whole page with the reason (422) when it refused.
     */
    private HTTPResponse after(WebRequest request, Account account, YearMonth month, Result<?, BusinessError> result,
                               Text done) {
        if (HtmxRequest.of(request).isHtmx()) return changed(account, month, outcome(result, done));
        if (result instanceof Result.Failure<?, BusinessError>(var error)) {
            return Layout.page(request, HTTPStatus.UNPROCESSABLE_CONTENT, Screen.STATEMENT, Ledger.monthNav(book, month),
                    StatementViews.screen(book, account, month, StatementViews.notice(Text.message(error.render()))));
        }
        return WebResponse.redirect("/statement?account=" + account.identity() + "&" + Ledger.MONTH + "=" + month);
    }

    /** The panel with a notice in the editor, and the list with the new balances. */
    private HTTPResponse changed(Account account, YearMonth month, Text notice) {
        return changed(account, month, StatementViews.notice(notice));
    }

    private HTTPResponse changed(Account account, YearMonth month, Node notice) {
        Node view = Html.fragment(StatementViews.panel(book, account, month, notice),
                StatementViews.accounts(book, account, month, true));
        return ok(view.render());
    }

    // ---------- account and month of the request

    private YearMonth month(Function<String, @Nullable String> value) {
        return Validators.monthOf(value.apply("month"), book::month);
    }

    /** The account asked, else the first of the list ({@link Book#moneyAccounts}), else the first of all. */
    private Account chosen(Function<String, @Nullable String> value) {
        return book.account(Validators.uuid(value.apply("account"))).orElseGet(() -> {
            val money = book.moneyAccounts();
            return money.isEmpty() ? book.tree().getFirst() : money.getFirst();
        });
    }
}
