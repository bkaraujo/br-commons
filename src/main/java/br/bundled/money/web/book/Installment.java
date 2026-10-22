package br.bundled.money.web.book;

import br.bundled.money.business._0_domain.model.Account;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A ledger line whose transaction is installment {@code number} of {@code total} (INTENT F10) in {@code account}: the
 * installment of the transaction when it has one, else the {@code k/N} a card bill writes in the description
 * ({@code LOJA 03/10}, {@code PARC 3/10}, {@code PARCELA 3 DE 10}); the last one counts, a full date
 * ({@code 05/10/2026}) is not one.
 */
@NullMarked
public record Installment(LedgerLine line, Account account, int number, int total) {

    private static final Pattern FRACTION = Pattern.compile("(?<![\\d/])(\\d{1,3})\\s*/\\s*(\\d{1,3})(?![\\d/])");
    private static final Pattern WORDS = Pattern.compile("(?i)\\bparc\\p{L}*\\.?\\s*(\\d{1,3})\\s+de\\s+(\\d{1,3})\\b");

    /** The installment of the line in {@code account}, when its transaction is one: 1 ≤ k ≤ N, N ≥ 2. */
    public static Optional<Installment> of(LedgerLine line, Account account) {
        val transaction = line.transaction();
        if (transaction.installmentTotal() > 1) {
            return Optional.of(new Installment(line, account, transaction.installment(), transaction.installmentTotal()));
        }
        Installment found = null;
        for (val pattern : new Pattern[] {FRACTION, WORDS}) {
            val matcher = pattern.matcher(transaction.description());
            while (matcher.find()) {
                val number = Integer.parseInt(matcher.group(1));
                val total = Integer.parseInt(matcher.group(2));
                if (total >= 2 && number >= 1 && number <= total) found = new Installment(line, account, number, total);
            }
        }
        return Optional.ofNullable(found);
    }

    /** The installments after this one. */
    public int left() {
        return total - number;
    }

    /** What the installments after this one add up to, each worth this one ({@link LedgerLine#amount()}). */
    public BigDecimal remaining() {
        return line.amount().multiply(BigDecimal.valueOf(left()));
    }
}
