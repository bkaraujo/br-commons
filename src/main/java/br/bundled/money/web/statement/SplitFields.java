package br.bundled.money.web.statement;

import br.bundled.money.web.mock.Book;
import br.bundled.money.web.mock.MockAccount;
import br.bundled.money.web.mock.MockSplit;
import br.bundled.money.web.mock.MockTransaction;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The complete transaction (INTENT F2): date, description and the lines of splits, each an account and the value
 * that enters it or leaves it (the words of "De/Para"; debit and credit stay out of sight). The lines repeat the
 * names {@link #ACCOUNT}, {@link #IN} and {@link #OUT}, in order; a blank line is ignored. Checked on the server: an
 * account below the top level per line, one value per line, at least two lines and what enters equal to what leaves.
 */
@NullMarked
final class SplitFields {

    static final Field ACCOUNT = FieldSpec.of("split-account", InputType.TEXT).build().getOrThrow();
    static final Field IN = FieldSpec.of("split-in", InputType.NUMBER).withMin("0.01").withStep("0.01").build().getOrThrow();
    static final Field OUT = FieldSpec.of("split-out", InputType.NUMBER).withMin("0.01").withStep("0.01").build().getOrThrow();
    /** The hidden marker of the form: the submission is a transaction of splits, not the simple one. */
    static final String MODE = "mode";
    static final String SPLITS = "splits";

    /** The violation of the lines as a whole: fewer than two ({@code REQUIRED}), unbalanced ({@code INVALID}). */
    static final String TOTAL = "splits";
    static final Field TOTAL_FIELD = FieldSpec.of(TOTAL, InputType.HIDDEN).build().getOrThrow();

    /** A line as typed. */
    record Line(String account, String in, String out) {

        static final Line BLANK = new Line("", "", "");

        boolean blank() {
            return account.isBlank() && in.isBlank() && out.isBlank();
        }
    }

    /** A checked transaction of splits, signed in the nature of each account. */
    record Entry(LocalDate date, String description, List<MockSplit> splits) {}

    private SplitFields() {}

    /** The lines submitted, in order. */
    static List<Line> lines(Function<String, List<String>> all) {
        val accounts = all.apply(ACCOUNT.name());
        val ins = all.apply(IN.name());
        val outs = all.apply(OUT.name());
        val lines = new ArrayList<Line>();
        for (var i = 0; i < accounts.size(); i++) {
            lines.add(new Line(accounts.get(i), at(ins, i), at(outs, i)));
        }
        return lines;
    }

    private static String at(List<String> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }

    /** The lines of a saved transaction: each split in debit terms, what enters or what leaves. */
    static List<Line> lines(Book book, MockTransaction transaction) {
        return transaction.splits().stream().map(split -> {
            val debit = book.debit(split);
            val value = debit.abs().toPlainString();
            return debit.signum() > 0 ? new Line(String.valueOf(split.account()), value, "")
                    : new Line(String.valueOf(split.account()), "", value);
        }).toList();
    }

    /** The simple form turned into lines: the value enters {@code to} and leaves {@code from}, plus a blank line. */
    static List<Line> lines(Map<String, String> simple) {
        val amount = simple.getOrDefault(StatementFields.AMOUNT.name(), "");
        return List.of(new Line(simple.getOrDefault(StatementFields.TO.name(), ""), amount, ""),
                new Line(simple.getOrDefault(StatementFields.FROM.name(), ""), "", amount), Line.BLANK);
    }

    /** The line violations are by position: {@code split-2}. */
    static String line(int index) {
        return "split-" + index;
    }

    static Result<Entry, List<Violation>> read(Function<String, String> value, List<Line> lines, Book book) {
        val head = List.of(StatementFields.DATE, StatementFields.DESCRIPTION);
        val violations = new ArrayList<>(Validation.check(head, name -> List.of(value.apply(name))));
        val splits = new ArrayList<MockSplit>();
        var balance = BigDecimal.ZERO;
        for (var i = 0; i < lines.size(); i++) {
            val line = lines.get(i);
            if (line.blank()) continue;
            val account = account(line.account(), book);
            val debit = debit(line);
            if (account == null || debit == null) {
                violations.add(new Violation(line(i), account == null ? ValidationRule.REQUIRED : ValidationRule.INVALID));
                continue;
            }
            balance = balance.add(debit);
            splits.add(new MockSplit(account.id(), debit.multiply(BigDecimal.valueOf(account.type().debit))));
        }
        val count = lines.stream().filter(line -> !line.blank()).count();
        if (count < 2) {
            violations.add(new Violation(TOTAL, ValidationRule.REQUIRED));
        } else if (violations.stream().noneMatch(violation -> violation.field().startsWith("split-")) && balance.signum() != 0) {
            violations.add(new Violation(TOTAL, ValidationRule.INVALID));
        }
        if (!violations.isEmpty()) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        val date = LocalDate.parse(value.apply(StatementFields.DATE.name()));
        Forms.open(StatementFields.DATE, date, book, violations);
        if (!violations.isEmpty()) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new Entry(date, value.apply(StatementFields.DESCRIPTION.name()).strip(), splits));
    }

    private static @Nullable MockAccount account(String id, Book book) {
        val number = Ui.number(id);
        return number == null ? null : book.account(number).filter(account -> account.parent() != null).orElse(null);
    }

    /** The value in debit terms: what enters positive, what leaves negative; one of them, above zero, 2 decimals. */
    private static @Nullable BigDecimal debit(Line line) {
        val in = amount(line.in());
        val out = amount(line.out());
        if (line.in().isBlank() == line.out().isBlank()) return null;
        return line.in().isBlank() ? (out == null ? null : out.negate()) : in;
    }

    static @Nullable BigDecimal amount(String text) {
        try {
            val value = new BigDecimal(text.strip());
            return value.signum() > 0 && value.stripTrailingZeros().scale() <= 2 ? value.setScale(2, RoundingMode.UNNECESSARY) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The message of a violation: the head fields as in the simple form, the lines and the total their own. */
    static String message(Violation violation) {
        if (TOTAL.equals(violation.field())) {
            return violation.rule() == ValidationRule.REQUIRED ? "Informe ao menos duas contas."
                    : "O que entra precisa ser igual ao que sai.";
        }
        if (violation.field().startsWith("split-")) {
            return violation.rule() == ValidationRule.REQUIRED ? "Escolha uma conta."
                    : "Informe Entra ou Sai (só um), maior que zero, com até 2 casas.";
        }
        return StatementFields.message(violation);
    }
}
