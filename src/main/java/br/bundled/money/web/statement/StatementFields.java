package br.bundled.money.web.statement;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.web.book.Book;
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
import java.util.Objects;
import java.util.function.Function;

/**
 * The fields of a transaction being edited, declared once: the view renders them and {@link #read} checks the
 * submission on the server, plus what a field alone cannot express: both accounts exist below the top level and
 * differ.
 */
@NullMarked
final class StatementFields {

    static final Field DATE = FieldSpec.of("date", InputType.DATE).asRequired().build().getOrThrow();
    static final Field DESCRIPTION = FieldSpec.of("description", InputType.TEXT).asRequired().withMaxLength(80).build().getOrThrow();
    /** The description of a transaction imported from a PDF: optional, its PDF name stands in when it is blank. */
    static final Field NAMED = FieldSpec.of("description", InputType.TEXT).withMaxLength(80).build().getOrThrow();
    static final Field AMOUNT = FieldSpec.of("amount", InputType.NUMBER).asRequired().withMin("0.01").withStep("0.01").build().getOrThrow();
    static final Field FROM = FieldSpec.of("from", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field TO = FieldSpec.of("to", InputType.TEXT).asRequired().build().getOrThrow();

    static final List<Field> ALL = List.of(DATE, DESCRIPTION, AMOUNT, FROM, TO);

    /** Shown only in the detail, read only: the day of the purchase, the installment, the note and the PDF name. */
    static final Field PURCHASED = FieldSpec.of("purchased", InputType.DATE).build().getOrThrow();
    static final Field INSTALLMENT = FieldSpec.of("installment", InputType.TEXT).build().getOrThrow();
    static final Field NOTE = FieldSpec.of("note", InputType.TEXT).build().getOrThrow();
    static final Field PDF_NAME = FieldSpec.of("pdf-name", InputType.TEXT).build().getOrThrow();

    /** The hidden id of the transaction being edited, carried to the form of splits. */
    static final String EDITING = "editing";

    /** A checked edit: {@code value} goes from {@code from} to {@code to}. */
    record Revision(LocalDate date, String description, BigDecimal value, Account from, Account to) {}

    private StatementFields() {}

    /** The description field of {@code transaction} (null: a new one): optional when it has a PDF name. */
    static Field description(@Nullable Transaction transaction) {
        return transaction != null && transaction.pdfName() != null ? NAMED : DESCRIPTION;
    }

    /** {@code editing}: the transaction being edited, null for a new one. */
    static Result<Revision, List<Violation>> read(Function<String, String> value, Book book, @Nullable Transaction editing) {
        val fields = Validation.check(List.of(DATE, description(editing), AMOUNT, FROM, TO), name -> List.of(value.apply(name)));
        if (!fields.isEmpty()) {
            return new Result.Failure<>(fields);
        }
        val violations = new ArrayList<Violation>();
        val from = Forms.account(FROM, value, book, account -> account.parent() != null, violations);
        val to = Forms.account(TO, value, book, account -> account.parent() != null, violations);
        if (from != null && from.equals(to)) {
            violations.add(new Violation(TO.name(), ValidationRule.INVALID));
        }
        val date = LocalDate.parse(value.apply(DATE.name()));
        Forms.open(DATE, date, book, violations);
        if (from == null || to == null || !violations.isEmpty()) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new Revision(date, value.apply(DESCRIPTION.name()).strip(),
                new BigDecimal(value.apply(AMOUNT.name())), from, to));
    }

    static String message(Violation violation) {
        return Forms.violation(violation, INVALID_MESSAGES);
    }

    private static final Map<String, String> INVALID_MESSAGES = Map.of(
            FROM.name(), "forms.statement.choose_account",
            TO.name(), "forms.statement.choose_other_account",
            DATE.name(), Forms.CLOSED);

    /** The submitted values, by field name, to show them again with the errors. */
    static Map<String, String> values(Function<String, String> value) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), value.apply(field.name())));
        return values;
    }

    /** The values of a saved transaction; date and description only when it is not simple. */
    static Map<String, String> values(Book book, Transaction transaction) {
        if (!book.simple(transaction)) {
            return Map.of(DATE.name(), transaction.date().toString(), DESCRIPTION.name(), transaction.description());
        }
        return Map.of(DATE.name(), transaction.date().toString(), DESCRIPTION.name(), transaction.description(),
                AMOUNT.name(), book.value(transaction).toPlainString(),
                FROM.name(), book.from(transaction).identity().toString(), TO.name(), book.to(transaction).identity().toString());
    }

    /**
     * {@link #values(Book, Transaction)} plus what only the detail shows ({@link #PURCHASED}, {@link #INSTALLMENT},
     * {@link #NOTE}, {@link #PDF_NAME}).
     */
    static Map<String, String> shown(Book book, Transaction transaction) {
        val values = new HashMap<>(values(book, transaction));
        val purchased = transaction.purchased();
        if (purchased != null) values.put(PURCHASED.name(), purchased.toString());
        values.put(INSTALLMENT.name(), transaction.installment() + "/" + transaction.installmentTotal());
        values.put(NOTE.name(), Objects.requireNonNullElse(transaction.note(), ""));
        values.put(PDF_NAME.name(), Objects.requireNonNullElse(transaction.pdfName(), ""));
        return values;
    }

    /** A new transaction: the date given and, below the top level, the account of the statement as the origin. */
    static Map<String, String> blank(LocalDate date, Account account) {
        return account.parent() == null ? Map.of(DATE.name(), date.toString())
                : Map.of(DATE.name(), date.toString(), FROM.name(), account.identity().toString());
    }
}
