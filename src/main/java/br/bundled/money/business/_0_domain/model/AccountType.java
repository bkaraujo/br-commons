package br.bundled.money.business._0_domain.model;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

public enum AccountType {
    BANK(AccountNature.FINANCE_ACCOUNT, -1),
    CASH(AccountNature.FINANCE_ACCOUNT, -1),
    CREDIT(AccountNature.FINANCE_ACCOUNT, 1),
    STOCK(AccountNature.FINANCE_ACCOUNT, -1),
    MUTUAL(AccountNature.FINANCE_ACCOUNT, -1),
    ASSET(AccountNature.LEDGER_ACCOUNT, -1),
    LIABILITY(AccountNature.LEDGER_ACCOUNT, 1),
    EQUITY(AccountNature.LEDGER_ACCOUNT, 1),
    INCOME(AccountNature.LEDGER_ACCOUNT, 1),
    EXPENSE(AccountNature.LEDGER_ACCOUNT, -1),
    ROOT(AccountNature.LEDGER_ACCOUNT, 1),
    RECEIVABLE(AccountNature.LEDGER_ACCOUNT, -1),
    PAYABLE(AccountNature.LEDGER_ACCOUNT, 1),
    TRADING(AccountNature.LEDGER_ACCOUNT, 1);

    private static final Set<AccountType> ASSETS = EnumSet.of(BANK, CASH, STOCK, MUTUAL, ASSET, RECEIVABLE);
    private static final Set<AccountType> LIABILITIES = EnumSet.of(CREDIT, LIABILITY, PAYABLE);

    public final AccountNature nature;
    public final int debit;
    public final int credit;

    AccountType(AccountNature nature, int credit) {
        this.nature = nature;
        this.credit = credit;
        this.debit = -credit;
    }

    /**
     * The amount, in this type's nature, of a split that debits {@code value} (GnuCash's value; the inverse of
     * {@link TransactionSplit#debitAmount}): a transfer debits where the value goes.
     */
    public BigDecimal debited(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(debit));
    }

    /** The amount, in this type's nature, of a split that credits {@code value}: a transfer credits where it leaves. */
    public BigDecimal credited(BigDecimal value) {
        return debited(value.negate());
    }

    /**
     * The family of the hierarchy (INTENT §6): a subaccount is of its parent's family. Assets (BANK, CASH, STOCK,
     * MUTUAL, ASSET, RECEIVABLE) answer {@link #ASSET}; liabilities (CREDIT, LIABILITY, PAYABLE) {@link #LIABILITY};
     * any other type is its own family.
     */
    public AccountType family() {
        if (ASSETS.contains(this)) return ASSET;
        return LIABILITIES.contains(this) ? LIABILITY : this;
    }

}
