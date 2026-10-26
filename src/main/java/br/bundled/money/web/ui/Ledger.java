package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.book.LedgerLine;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/** What the ledger, the statement and the reports share: the month's summary, the columns and the month navigation (in the header). */
@NullMarked
public final class Ledger {

    /** The query parameter of the month navigation ({@code month=2026-10}). */
    public static final String MONTH = "month";

    private Ledger() {}

    /** Opening, increases, reductions and closing of the account in the month of {@code lines}. */
    public static Node summary(Book book, Account account, YearMonth month, List<LedgerLine> lines) {
        val opening = book.balance(account, month.atDay(1).minusDays(1));
        val in = lines.stream().map(LedgerLine::amount).filter(amount -> amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        val out = lines.stream().map(LedgerLine::amount).filter(amount -> amount.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return dl(cls("summary"),
                div(dt(text(message("forms.ledger.opening"))), dd(Ui.amount(opening, false))),
                div(dt(text(message("forms.ledger.increases"))), dd(Ui.amount(in, true))),
                div(dt(text(message("forms.ledger.reductions"))), dd(Ui.amount(out, true))),
                div(cls("closing"), dt(text(message("forms.ledger.closing"))), dd(Ui.amount(opening.add(in).add(out), false))));
    }

    /** Date, description and value; {@code extra} columns after them. No running balance: the summary has the month's. */
    public static Node columns(Node... extra) {
        return thead(tr(th(text(message("forms.common.date"))), th(text(message("forms.common.description"))),
                th(cls("num"), text(message("forms.common.value"))), fragment(extra)));
    }

    /**
     * The detail of a transaction ({@code StatementRoutes}), opened from {@code account} in {@code month}: its link to
     * the statement goes back there.
     */
    public static String detail(Transaction transaction, Account account, YearMonth month) {
        return "/statement/transactions/" + transaction.identity() + "?account=" + account.identity() + "&" + MONTH + "="
                + month;
    }

    /**
     * The description cell of a line: a link to the transaction's {@link #detail}, opened as a dialog over the screen
     * ({@link Layout#ENTRY_MODAL}; without JavaScript, its page), the counterpart below it.
     */
    public static Node described(Transaction transaction, String counterpart, Account account, YearMonth month) {
        val url = detail(transaction, account, month);
        return td(a(cls("detail-link"), href(url), hxGet(url), hxTarget("#" + Layout.ENTRY_MODAL), hxSwap(HxSwap.INNER_HTML),
                        strong(text(Text.data(transaction.label())))),
                small(cls("sub"), text(Text.data(counterpart))));
    }

    /**
     * The month navigation of the header ({@link Layout}), on every screen with a month: previous, the month picked from
     * a list, next and (away from it) the current month. It is the same on all of them: {@code month.js} points each
     * control at the URL the page is on with {@link #MONTH} replaced, a boosted navigation that keeps the other
     * parameters ({@code /statement?account=…}). Without JavaScript the links keep only the month. Only what it shows
     * is read here; the markup is built when it goes ({@link MonthNav#node}).
     */
    public static MonthNav monthNav(Book book, YearMonth month) {
        val first = book.firstMonth();
        return new MonthNav(month, YearMonth.from(book.today()), first.isBefore(month) ? first : month);
    }

    /**
     * The month navigation of a screen: the month shown, the current one (today's) and the first of the list (the
     * book's first, or the month shown when before it).
     */
    public record MonthNav(YearMonth month, YearMonth current, YearMonth first) {

        /**
         * What it shows, in a few characters: the same navigation gives the same key, so the header can tell it is
         * already there without building it ({@link Layout#MONTH_KEY}).
         */
        public String key() {
            return month + "_" + current + "_" + first;
        }

        public Node node() {
            return nav(
                    cls("month-nav"),
                    ariaLabel(message("forms.common.month")),
                    monthLink(month.minusMonths(1), "←", "forms.ledger.previous_month"),
                    monthSelect(),
                    monthLink(month.plusMonths(1), "→", "forms.ledger.next_month"),
                    when(!month.equals(current), () -> monthLink(current, Ui.string("forms.ledger.today"), "forms.ledger.current_month")),
                    a(cls("month-go"), href("?" + MONTH + "=" + month), hidden(), ariaHidden(true))
            );
        }

        /**
         * The months from the first to a year past the current one, the latest first ({@code combo.js} filters them
         * while typing); {@code month.js} goes to the one picked.
         */
        private Node monthSelect() {
            val last = (current.isAfter(month) ? current : month).plusMonths(12);
            val months = Stream.iterate(last, shown -> !shown.isBefore(first), shown -> shown.minusMonths(1)).toList();
            return select(name(MONTH), data("month-pick", ""), ariaLabel(message("forms.common.month")),
                    each(months, shown -> shown.equals(month)
                            ? option(value(shown.toString()), selected(), text(Text.data(Ui.month(shown))))
                            : option(value(shown.toString()), text(Text.data(Ui.month(shown))))));
        }
    }

    private static Node monthLink(YearMonth month, String content, String label) {
        return a(href("?" + MONTH + "=" + month), data("month", month.toString()), ariaLabel(message(label)),
                text(Text.data(content)));
    }
}
