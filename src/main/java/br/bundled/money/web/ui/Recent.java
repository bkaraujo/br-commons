package br.bundled.money.web.ui;

import br.bundled.money.web.mock.Book;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;

/** The latest transactions, as the home and the quick entry show them: what, where, when and the net effect. */
@NullMarked
public final class Recent {

    private Recent() {}

    public static Node list(Book book, int limit) {
        val transactions = book.recent(limit);
        return section(
                cls("recent card"),
                h3(text(message("Últimos lançamentos"))),
                transactions.isEmpty()
                        ? p(cls("empty"), text(message("Nenhum lançamento.")))
                        : ul(each(transactions, transaction -> {
                            val effect = book.effect(transaction);
                            return li(
                                    div(cls("what"),
                                            strong(text(Text.data(transaction.description()))),
                                            small(text(Text.data(book.label(transaction))))),
                                    div(cls("how"),
                                            effect.signum() == 0 ? Ui.amount(book.value(transaction), false)
                                                    : Ui.amount(effect, true),
                                            small(time(datetime(transaction.date().toString()),
                                                    text(Text.data(Ui.day(transaction.date())))))));
                        }))
        );
    }
}
