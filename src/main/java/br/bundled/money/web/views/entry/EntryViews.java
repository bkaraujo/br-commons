package br.bundled.money.web.views.entry;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.views.Layout;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Ui;
import br.bundled.money.web.views.tools.Formatters;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.Html;
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

import static br.bundled.money.web.views.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The quick entry: date and value (bold, as tall as the others) side by side, then account, category, description and an optional note. It
 * opens as a dialog over
 * the screen the user is on ({@link #modal}, from the menu), without the latest transactions; the page
 * ({@link #screen}) is only what a browser without htmx gets from {@code /entries/new}. In the page, the whole
 * {@code #quick} is swapped on saving: the notice of what was saved and the form ready for the next one.
 */
@NullMarked
final class EntryViews {

    /** The hidden marker of the dialog's form: saving it reloads the content behind instead of swapping a page. */
    static final String MODAL = "modal";

    private EntryViews() {}

    /** The page. {@code refused}: why the service did not save it (a rule of the domain), shown above the form. */
    static Node screen(Book book, Map<String, String> values, List<Violation> errors, @Nullable Transaction saved,
                       @Nullable String refused) {
        return section(
                id("quick"), cls("quick"),
                h2(text(message("forms.entry.page_title"))),
                saved == null ? fragment() : notice(book, saved),
                refused == null ? fragment() : p(cls("error"), role("alert"), text(Text.data(refused))),
                entryForm(book, values, errors, null, false)
        );
    }

    /** The dialog: the form alone, a card in {@code #entry-modal}; invalid, it comes back in place with its errors. */
    static Node modal(Book book, Map<String, String> values, List<Violation> errors, @Nullable String refused) {
        return entryForm(book, values, errors, refused, true);
    }

    private static Node entryForm(Book book, Map<String, String> values, List<Violation> errors,
                                  @Nullable String refused, boolean modal) {
        return form(
                id("entry-form"), cls("quick-form card"), method(FormMethod.POST), action("/entries"),
                hxPost("/entries"), hxTarget(modal ? "#entry-form" : "#quick"), hxSwap(HxSwap.OUTER_HTML),
                modal ? role("dialog") : fragment(),
                modal ? fragment(input(type("hidden"), name(MODAL), value("1")), h3(text(message("forms.entry.title")))) : fragment(),
                modal && refused != null ? p(cls("error wide"), role("alert"), text(Text.data(refused))) : fragment(),
                labeled(EntryFields.DATE, "forms.common.date", values, errors, null),
                labeled(EntryFields.AMOUNT, "forms.entry.amount", values, errors, null, cls("amount-input"), inputmode(InputMode.NUMERIC), data("money", ""), placeholder(Text.data("0,00")), autofocus()),
                labeled(EntryFields.ACCOUNT, "forms.common.account", values, errors, options(book, book.moneyAccounts(), false)),
                labeled(EntryFields.CATEGORY, "forms.common.category", values, errors, options(book, book.categories(), true)),
                labeled(EntryFields.DESCRIPTION, "forms.common.description", values, errors, null, placeholder(message("forms.entry.description_hint"))),

                Forms.labeledText("quick", EntryFields.NOTE, "forms.entry.note", values, errors, EntryFields::message, rows(10)),
                modal ? div(cls("buttons wide"),
                        button(type(ButtonType.SUBMIT), text(message("forms.entry.submit"))),
                        button(type(ButtonType.BUTTON), cls("link"), hxGet(Layout.ENTRY_EDITOR),
                                hxTarget("#" + Layout.ENTRY_MODAL), hxSwap(HxSwap.INNER_HTML),
                                text(message("forms.common.cancel"))))
                        : button(type(ButtonType.SUBMIT), text(message("forms.entry.submit")))
        );
    }

    private static Node notice(Book book, Transaction saved) {
        val account = saved.splits().stream().map(TransactionSplit::account).filter(Account::financial).findFirst();
        return p(cls("notice"), role("status"),
                text(message("forms.entry.saved", saved.description(), Formatters.money(book.value(saved)))),
                account.map(found -> (Node) a(href(Screen.STATEMENT.path() + "?account=" + found.identity()),
                        text(message("forms.entry.view_in", found.name())))).orElseGet(Html::fragment));
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options, Content... extra) {
        return Forms.labeled("quick", field, label, values, errors, options, EntryFields::message, extra);
    }

    /** A prompt, then the accounts: categories by their path, financial ones by type and name (the type when it adds). */
    private static List<Option> options(Book book, List<Account> accounts, boolean path) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("forms.common.choose")));
        accounts.forEach(account -> options.add(new Option(account.identity().toString(), Text.data(path ? book.path(account)
                : account.name().equals(Ui.typeLabel(account.type())) ? account.name()
                : Ui.string("forms.entry.account_option", Ui.typeLabel(account.type()), account.name())))));
        return options;
    }
}
