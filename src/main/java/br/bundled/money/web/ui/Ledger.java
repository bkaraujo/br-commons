package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.book.LedgerLine;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;

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
                div(dt(text(message("Saldo inicial"))), dd(Ui.amount(opening, false))),
                div(dt(text(message("Aumentos"))), dd(Ui.amount(in, true))),
                div(dt(text(message("Reduções"))), dd(Ui.amount(out, true))),
                div(cls("closing"), dt(text(message("Saldo final"))), dd(Ui.amount(opening.add(in).add(out), false))));
    }

    /** Date, description, value and running balance; {@code extra} columns after them. */
    public static Node columns(Node... extra) {
        return thead(tr(th(text(message("Data"))), th(text(message("Descrição"))),
                th(cls("num"), text(message("Valor"))), th(cls("num"), text(message("Saldo"))), fragment(extra)));
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
                    ariaLabel(message("Mês")),
                    monthLink(month.minusMonths(1), "←", "Mês anterior"),
                    monthSelect(),
                    monthLink(month.plusMonths(1), "→", "Próximo mês"),
                    when(!month.equals(current), () -> monthLink(current, "Hoje", "Mês atual")),
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
            return select(name(MONTH), data("month-pick", ""), ariaLabel(message("Mês")),
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
