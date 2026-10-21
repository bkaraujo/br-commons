package br.bundled.money.business._0_domain.model;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.UUID;

/** A split as the book keeps it: immutable, its account and the amount in the account's nature. */
@NullMarked
public final class BookSplit extends TransactionSplit {

    private final UUID id;
    private final Account account;
    private final BigDecimal amount;
    private final long createdAt;
    private final long updatedAt;

    public BookSplit(UUID id, Account account, BigDecimal amount, long createdAt, long updatedAt) {
        this.id = id;
        this.account = account;
        this.amount = amount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new split, now. */
    public static BookSplit of(Account account, BigDecimal amount) {
        val now = System.currentTimeMillis();
        return new BookSplit(UUID.randomUUID(), account, amount, now, now);
    }

    @Override public UUID identity() { return id; }
    @Override public Account account() { return account; }
    @Override public BigDecimal amount() { return amount; }
    @Override public long createdAt() { return createdAt; }
    @Override public long updatedAt() { return updatedAt; }
}
