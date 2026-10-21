package br.bundled.money.web.statement;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Layout;
import br.bundled.money.web.ui.Screen;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static br.bundled.money.web.ui.Ui.ok;
import static br.bundled.money.web.ui.Ui.pathId;

/**
 * {@code GET /statement?account=3&month=2026-10}: the whole page, or only {@code #statement-panel} when the list or
 * the month navigation asks. Without a valid account, the first financial one. The editor
 * ({@code #statement-editor}) receives the form ({@code /statement/transactions/new},
 * {@code /statement/transactions/{id}/edit}) or the confirmation ({@code /statement/transactions/{id}/delete});
 * creating, saving and deleting answer with the panel of the account and month sent along, plus the list out of
 * band; invalid, the form with its errors. The simple form or the form of splits (marked by {@link SplitFields#MODE}),
 * re-rendered by {@code /statement/transactions/split} as lines are added or removed. Literal routes come before
 * {@code {id}}.
 */
@NullMarked
public final class StatementRoutes implements WebResource {

    /** A transaction before the closing date (INTENT F9) is neither edited nor deleted. */
    private static final String CLOSED = "Lançamento em período fechado: recue a data-limite para alterá-lo.";

    private final Book book = Context.get(Book.class);

    @Override
    public void routes(Router router) {
        router.get("/statement", MediaType.TEXT_HTML, String.class, this::statement).getOrThrow();
        router.get("/statement/editor", MediaType.TEXT_HTML, String.class, _ -> ok("")).getOrThrow();
        router.get("/statement/transactions/new", MediaType.TEXT_HTML, String.class, this::blank).getOrThrow();
        router.get(StatementViews.SPLIT, MediaType.TEXT_HTML, String.class, this::split).getOrThrow();
        router.post("/statement/transactions", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/statement/transactions/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/statement/transactions/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.get("/statement/transactions/{id}/delete", MediaType.TEXT_HTML, String.class, this::confirm).getOrThrow();
        router.delete("/statement/transactions/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse statement(WebRequest request) {
        val month = month(request::parameter);
        val account = chosen(request::parameter);
        return Ui.partial(request) ? ok(StatementViews.panel(book, account, month, Html.fragment()).render())
                : Layout.page(request, Screen.STATEMENT, StatementViews.screen(book, account, month));
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
        val remove = Ui.number(form.value("remove"));
        if (remove != null && remove >= 0 && remove < lines.size() && lines.size() > 2) lines.remove((int) remove);
        if (!form.value("add").isEmpty() || lines.size() < 2) lines.add(SplitFields.Line.BLANK);
        val transaction = editing(form::value).orElse(null);
        return ok(StatementViews.splitForm(book, transaction, values, lines, List.of(), chosen(form::value),
                month(form::value)).render());
    }

    /** The transaction being edited ({@link StatementFields#EDITING}), while it can still be changed. */
    private Optional<Transaction> editing(Function<String, @Nullable String> value) {
        return book.transaction(Ui.uuid(value.apply(StatementFields.EDITING))).filter(transaction -> !book.closed(transaction));
    }

    private HTTPResponse confirm(WebRequest request) {
        return book.transaction(pathId(request))
                .map(transaction -> book.closed(transaction) ? ok("")
                        : ok(StatementViews.deletePanel(book, transaction, chosen(request::parameter), month(request::parameter)).render()))
                .orElseGet(() -> ok(""));
    }

    // ---------- update, delete

    /** Without htmx: post/redirect/get to the statement of the account and month. */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val account = chosen(form::value);
        val month = month(form::value);
        if (SplitFields.SPLITS.equals(form.value(SplitFields.MODE))) {
            val lines = SplitFields.lines(form::all);
            return switch (SplitFields.read(form::value, lines, book)) {
                case Result.Success<SplitFields.Entry, List<Violation>>(var entry) -> {
                    val posted = book.enter(entry.date(), entry.description(), entry.splits());
                    yield HtmxRequest.of(request).isHtmx()
                            ? changed(account, month, outcome(posted, "Lançamento \"" + entry.description() + "\" criado."))
                            : WebResponse.redirect("/statement?account=" + account.identity() + "&month=" + month);
                }
                case Result.Failure<SplitFields.Entry, List<Violation>>(var errors) -> invalid(StatementViews.splitForm(
                        book, null, StatementFields.values(form::value), lines, errors, account, month));
            };
        }
        return switch (StatementFields.read(form::value, book)) {
            case Result.Success<StatementFields.Revision, List<Violation>>(var revision) -> {
                val posted = book.move(revision.date(), revision.description(), revision.from(), revision.to(), revision.value());
                yield HtmxRequest.of(request).isHtmx()
                        ? changed(account, month, outcome(posted, "Lançamento \"" + revision.description() + "\" criado."))
                        : WebResponse.redirect("/statement?account=" + account.identity() + "&month=" + month);
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
            return changed(account, month, "O lançamento não existe mais.");
        }
        if (book.closed(found.get())) {
            return changed(account, month, CLOSED);
        }
        if (SplitFields.SPLITS.equals(form.value(SplitFields.MODE))) {
            val lines = SplitFields.lines(form::all);
            return switch (SplitFields.read(form::value, lines, book)) {
                case Result.Success<SplitFields.Entry, List<Violation>>(var entry) -> {
                    val revised = book.rewrite(found.get(), entry.date(), entry.description(), entry.splits());
                    yield changed(account, month, outcome(revised, "Lançamento \"" + entry.description() + "\" atualizado."));
                }
                case Result.Failure<SplitFields.Entry, List<Violation>>(var errors) -> invalid(StatementViews.splitForm(
                        book, found.get(), StatementFields.values(form::value), lines, errors, account, month));
            };
        }
        if (!book.simple(found.get())) {
            return changed(account, month, "Lançamento com mais de duas contas: use a divisão em várias contas.");
        }
        return switch (StatementFields.read(form::value, book)) {
            case Result.Success<StatementFields.Revision, List<Violation>>(var revision) -> {
                val revised = book.revise(found.get(), revision.date(), revision.description(), revision.from(),
                        revision.to(), revision.value());
                yield changed(account, month, outcome(revised, "Lançamento \"" + revision.description() + "\" atualizado."));
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
            return changed(account, month, CLOSED);
        }
        return changed(account, month, found.map(transaction -> outcome(book.discard(transaction),
                "Lançamento \"" + transaction.description() + "\" excluído.")).orElse("O lançamento não existe mais."));
    }

    /** {@code done} when the service did it; otherwise the reason it gave (a rule of the domain). */
    private static String outcome(Result<?, BusinessError> result, String done) {
        return result instanceof Result.Failure<?, BusinessError>(var error) ? error.render() : done;
    }

    /** The panel with a notice in the editor, and the list with the new balances. */
    private HTTPResponse changed(Account account, YearMonth month, String notice) {
        Node view = Html.fragment(StatementViews.panel(book, account, month, StatementViews.notice(notice)),
                StatementViews.accounts(book, account, true));
        return ok(view.render());
    }

    // ---------- account and month of the request

    private YearMonth month(Function<String, @Nullable String> value) {
        return Ui.monthOf(value.apply("month"), book.month());
    }

    private Account chosen(Function<String, @Nullable String> value) {
        val fallback = book.financial().isEmpty() ? book.tree().getFirst() : book.financial().getFirst();
        return book.account(Ui.uuid(value.apply("account"))).orElse(fallback);
    }
}
