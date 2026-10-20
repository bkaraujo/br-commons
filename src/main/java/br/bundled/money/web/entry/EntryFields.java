package br.bundled.money.web.entry;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.mock.QuickEntry;
import br.bundled.money.web.ui.Forms;
import br.commons.Result;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
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

    static final Field AMOUNT = FieldSpec.of("amount", InputType.NUMBER).asRequired().withMin("0.01").withStep("0.01").build().getOrThrow();
    static final Field CATEGORY = FieldSpec.of("category", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field ACCOUNT = FieldSpec.of("account", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field DESCRIPTION = FieldSpec.of("description", InputType.TEXT).asRequired().withMaxLength(80).build().getOrThrow();
    static final Field DATE = FieldSpec.of("date", InputType.DATE).asRequired().build().getOrThrow();

    static final List<Field> ALL = List.of(AMOUNT, CATEGORY, ACCOUNT, DESCRIPTION, DATE);

    private EntryFields() {}

    /** The checked values, or every violation found. */
    static Result<QuickEntry, List<Violation>> read(Function<String, String> value, Book book) {
        val fields = Validation.check(ALL, name -> List.of(value.apply(name)));
        if (!fields.isEmpty()) {
            return new Result.Failure<>(fields);
        }
        val violations = new ArrayList<Violation>();
        val category = Forms.account(CATEGORY, value, book, MockAccount::category, violations);
        val account = Forms.account(ACCOUNT, value, book, MockAccount::financial, violations);
        if (category == null || account == null) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new QuickEntry(LocalDate.parse(value.apply(DATE.name())),
                new BigDecimal(value.apply(AMOUNT.name())), account, category, value.apply(DESCRIPTION.name()).strip()));
    }

    static String message(Violation violation) {
        return Forms.violation(violation, INVALID_MESSAGES);
    }

    private static final Map<String, String> INVALID_MESSAGES = Map.of(
            CATEGORY.name(), "Escolha uma categoria.",
            ACCOUNT.name(), "Escolha uma conta.");

    /** The submitted values, by field name, to show them again with the errors. */
    static Map<String, String> values(Function<String, String> value) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), value.apply(field.name())));
        return values;
    }

    /** An empty form with the date and the account of the previous entry: several entries in a row go faster. */
    static Map<String, String> blank(LocalDate date, @Nullable Integer account) {
        return account == null ? Map.of(DATE.name(), date.toString())
                : Map.of(DATE.name(), date.toString(), ACCOUNT.name(), String.valueOf(account));
    }
}
