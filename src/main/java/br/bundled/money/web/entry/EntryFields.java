package br.bundled.money.web.entry;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.book.Book;
import br.bundled.money.web.ui.Forms;
import br.commons.Result;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The fields of the quick entry, declared once: the view renders them (HTML 5 types and constraints) and
 * {@link #read} checks the submission on the server, plus what a field alone cannot express: the account is
 * financial and the category is an income or expense.
 */
@NullMarked
final class EntryFields {

    /** Text in reais ({@code 1.234,56}): {@code money.js} formats it while typing; {@link Forms#money} reads it. */
    static final Field AMOUNT = FieldSpec.of("amount", InputType.TEXT).asRequired().withMaxLength(20).build().getOrThrow();
    static final Field CATEGORY = FieldSpec.of("category", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field ACCOUNT = FieldSpec.of("account", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field DESCRIPTION = FieldSpec.of("description", InputType.TEXT).asRequired().withMaxLength(80).build().getOrThrow();
    static final Field DATE = FieldSpec.of("date", InputType.DATE).asRequired().build().getOrThrow();
    /** Optional free text, shown only here and in the transaction's detail. */
    static final Field NOTE = FieldSpec.of("note", InputType.TEXT).withMaxLength(500).build().getOrThrow();

    static final List<Field> ALL = List.of(DATE, AMOUNT, ACCOUNT, CATEGORY, DESCRIPTION, NOTE);

    private EntryFields() {}

    /** The checked values, or every violation found. */
    static Result<QuickEntry, List<Violation>> read(Function<String, String> value, Book book) {
        val fields = Validation.check(ALL, name -> List.of(value.apply(name)));
        if (!fields.isEmpty()) {
            return new Result.Failure<>(fields);
        }
        val violations = new ArrayList<Violation>();
        val amount = Forms.money(value.apply(AMOUNT.name()));
        if (amount == null) violations.add(new Violation(AMOUNT.name(), ValidationRule.INVALID));
        else if (amount.signum() <= 0) violations.add(new Violation(AMOUNT.name(), ValidationRule.BELOW_MIN));
        val category = Forms.account(CATEGORY, value, book, Book::category, violations);
        val account = Forms.account(ACCOUNT, value, book, Account::financial, violations);
        val date = LocalDate.parse(value.apply(DATE.name()));
        Forms.open(DATE, date, book, violations);
        if (amount == null || category == null || account == null || !violations.isEmpty()) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new QuickEntry(date, amount, account, category, value.apply(DESCRIPTION.name()).strip(),
                value.apply(NOTE.name())));
    }

    static String message(Violation violation) {
        return Forms.violation(violation, INVALID_MESSAGES);
    }

    private static final Map<String, String> INVALID_MESSAGES = Map.of(
            AMOUNT.name(), "forms.entry.amount_format",
            CATEGORY.name(), "forms.entry.choose_category",
            ACCOUNT.name(), "forms.entry.choose_account",
            DATE.name(), Forms.CLOSED);

    /** The submitted values, by field name, to show them again with the errors. */
    static Map<String, String> values(Function<String, String> value) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), value.apply(field.name())));
        return values;
    }

    /** An empty form with the date and the account of the previous entry: several entries in a row go faster. */
    static Map<String, String> blank(LocalDate date, @Nullable Account account) {
        return account == null ? Map.of(DATE.name(), date.toString())
                : Map.of(DATE.name(), date.toString(), ACCOUNT.name(), account.identity().toString());
    }
}
