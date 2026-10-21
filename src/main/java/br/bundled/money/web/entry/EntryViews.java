package br.bundled.money.web.entry;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Recent;
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
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.bundled.money.web.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The quick entry: the value first and big, then category, account, description and date. The whole
 * {@code #quick} is swapped on saving: the notice of what was saved, the form and the latest transactions.
 */
@NullMarked
final class EntryViews {

    private EntryViews() {}

    /** {@code refused}: why the service did not save it (a rule of the domain), shown above the form. */
    static Node screen(Book book, Map<String, String> values, List<Violation> errors, @Nullable Transaction saved,
                       @Nullable String refused) {
        return section(
                id("quick"), cls("quick"),
                h2(text(message("Lançamento rápido"))),
                saved == null ? fragment() : notice(book, saved),
                refused == null ? fragment() : p(cls("error"), role("alert"), text(Text.data(refused))),
                form(
                        id("entry-form"), cls("quick-form card"), method(FormMethod.POST), action("/entries"),
                        hxPost("/entries"), hxTarget("#quick"), hxSwap(HxSwap.OUTER_HTML),
                        labeled(EntryFields.AMOUNT, "Valor (R$)", values, errors, null,
                                cls("amount-input"), inputmode(InputMode.DECIMAL), placeholder(Text.data("0,00")), autofocus()),
                        labeled(EntryFields.CATEGORY, "Categoria", values, errors, options(book, book.categories(), true)),
                        labeled(EntryFields.ACCOUNT, "Conta", values, errors, options(book, book.moneyAccounts(), false)),
                        labeled(EntryFields.DESCRIPTION, "Descrição", values, errors, null,
                                placeholder(message("Ex.: mercado, salário…"))),
                        labeled(EntryFields.DATE, "Data", values, errors, null),
                        button(type(ButtonType.SUBMIT), text(message("Lançar")))
                ),
                Recent.list(book, 5)
        );
    }

    private static Node notice(Book book, Transaction saved) {
        val account = saved.splits().stream().map(TransactionSplit::account).filter(Account::financial).findFirst();
        return p(cls("notice"), role("status"),
                text(message("Lançado: " + saved.description() + " · " + Ui.money(book.value(saved)) + ". ")),
                account.map(found -> (Node) a(href(Screen.STATEMENT.path() + "?account=" + found.identity()),
                        text(message("Ver em " + found.name())))).orElseGet(() -> fragment()));
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("quick", field, label, values, errors, options, EntryFields::message, extra);
    }

    /** A prompt, then the accounts: categories by their path, financial ones by name and type (when it adds). */
    private static List<Option> options(Book book, List<Account> accounts, boolean path) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("Escolha…")));
        accounts.forEach(account -> options.add(new Option(account.identity().toString(), Text.data(path ? book.path(account)
                : account.name().equals(Ui.typeLabel(account.type())) ? account.name()
                : account.name() + " · " + Ui.typeLabel(account.type())))));
        return options;
    }
}
