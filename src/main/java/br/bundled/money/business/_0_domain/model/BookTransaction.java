package br.bundled.money.business._0_domain.model;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A transaction as the book keeps it: immutable; a change is a {@link #revised} copy with the same identity. The
 * description is the {@link #description()} of the entity. Not an installment unless built as one.
 */
@NullMarked
public final class BookTransaction extends Transaction {

    /** What a transaction has besides its identity, date, description and splits (INTENT F10 installments). */
    public record Details(TransactionStatus status, int installment, int installmentTotal, BigDecimal installmentAmount,
                          @Nullable UUID purchase, @Nullable LocalDate purchased) {

        public Details(TransactionStatus status, int installment, int installmentTotal, BigDecimal installmentAmount,
                       @Nullable UUID purchase) {
            this(status, installment, installmentTotal, installmentAmount, purchase, null);
        }

        /** Not an installment: one of one, worth the whole {@code value}. */
        public static Details single(BigDecimal value) {
            return new Details(TransactionStatus.CONFIRMED, 1, 1, value, null, null);
        }
    }

    private final UUID id;
    private final LocalDate date;
    private final List<TransactionSplit> splits;
    private final Details details;
    private final long createdAt;
    private final long updatedAt;

    public BookTransaction(UUID id, LocalDate date, String description, List<? extends TransactionSplit> splits,
                           Details details, long createdAt, long updatedAt) {
        this.id = id;
        this.date = date;
        this.splits = List.copyOf(splits);
        this.details = details;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        description(description);
    }

    /** A new transaction, now; not an installment. */
    public static BookTransaction of(LocalDate date, String description, List<? extends TransactionSplit> splits) {
        return of(date, description, splits, null);
    }

    /** A new transaction, now, bought on {@code purchased} (null: the same day as {@code date}). */
    public static BookTransaction of(LocalDate date, String description, List<? extends TransactionSplit> splits,
                                     @Nullable LocalDate purchased) {
        val now = System.currentTimeMillis();
        val single = Details.single(value(splits));
        val details = new Details(single.status(), 1, 1, single.installmentAmount(), null, purchased);
        return new BookTransaction(UUID.randomUUID(), date, description, splits, details, now, now);
    }

    /** The same transaction (identity, creation, status) with new date, description and splits, changed now. */
    public BookTransaction revised(LocalDate date, String description, List<? extends TransactionSplit> splits) {
        // the purchase day belongs to the date it was posted on: moving the date drops it
        val purchased = date.equals(this.date) ? details.purchased() : null;
        val kept = details.installmentTotal() == 1
                ? new Details(details.status(), 1, 1, value(splits), null, purchased) : details;
        return new BookTransaction(id, date, description, splits, kept, createdAt, System.currentTimeMillis());
    }

    /** What moves: the sum of the debits (GnuCash's positive values). */
    public static BigDecimal value(List<? extends TransactionSplit> splits) {
        return splits.stream().map(TransactionSplit::debitAmount).filter(amount -> amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Details details() { return details; }

    @Override public UUID identity() { return id; }
    @Override public LocalDate date() { return date; }
    @Override public int installment() { return details.installment(); }
    @Override public int installmentTotal() { return details.installmentTotal(); }
    @Override public BigDecimal installmentAmount() { return details.installmentAmount(); }
    @Override public @Nullable UUID purchase() { return details.purchase(); }
    @Override public @Nullable LocalDate purchased() { return details.purchased(); }
    @Override public TransactionStatus status() { return details.status(); }
    @Override public List<TransactionSplit> splits() { return splits; }
    @Override public long createdAt() { return createdAt; }
    @Override public long updatedAt() { return updatedAt; }
}
