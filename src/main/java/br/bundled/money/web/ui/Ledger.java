package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.Account;
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

/** What the ledger, the statement and the reports share: the month's summary, the columns and the month navigation. */
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
                th(cls("num"), text(message("Valor"))), th(cls("num hide-narrow"), text(message("Saldo"))), fragment(extra)));
    }

    /**
     * Previous, the month picked from a list, next and (away from it) the current month; each loads {@code path} with
     * {@link #MONTH} into {@code target} and pushes its URL. {@code path} may carry other parameters
     * ({@code /statement?account=…}).
     */
    public static Node monthNav(Book book, YearMonth month, String path, String target) {
        val current = YearMonth.from(book.today());
        val separator = path.contains("?") ? "&" : "?";
        return nav(
                cls("month-nav"),
                monthLink(path + separator + MONTH + "=" + month.minusMonths(1), target, "←", "Mês anterior"),
                monthSelect(book, month, current, path, target),
                monthLink(path + separator + MONTH + "=" + month.plusMonths(1), target, "→", "Próximo mês"),
                when(!month.equals(current), () -> monthLink(path + separator + MONTH + "=" + current, target, "Hoje", "Mês atual"))
        );
    }

    /**
     * The months from the book's first to a year past the current one, the latest first ({@code combo.js} filters them
     * while typing); picking one sends {@link #MONTH} along with {@code path}.
     */
    private static Node monthSelect(Book book, YearMonth month, YearMonth current, String path, String target) {
        val first = book.firstMonth().isBefore(month) ? book.firstMonth() : month;
        val last = (current.isAfter(month) ? current : month).plusMonths(12);
        val months = Stream.iterate(last, shown -> !shown.isBefore(first), shown -> shown.minusMonths(1)).toList();
        return select(name(MONTH), ariaLabel(message("Mês")), hxGet(path), hxTarget(target), hxSwap(HxSwap.OUTER_HTML),
                hxPushUrl(true),
                each(months, shown -> shown.equals(month)
                        ? option(value(shown.toString()), selected(), text(Text.data(Ui.month(shown))))
                        : option(value(shown.toString()), text(Text.data(Ui.month(shown))))));
    }

    private static Node monthLink(String url, String target, String content, String label) {
        return a(href(url), hxGet(url), hxTarget(target), hxSwap(HxSwap.OUTER_HTML),
                hxPushUrl(true), ariaLabel(message(label)), text(Text.data(content)));
    }
}
