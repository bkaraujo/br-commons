package br.bundled.money.business._0_domain.model;

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

    public final AccountNature nature;
    public final int debit;
    public final int credit;

    AccountType(AccountNature nature, int credit) {
        this.nature = nature;
        this.credit = credit;
        this.debit = -credit;
    }

    /**
     * The family of the hierarchy (INTENT §6): a subaccount is of its parent's family. Assets (BANK, CASH, STOCK,
     * MUTUAL, ASSET, RECEIVABLE) answer {@link #ASSET}; liabilities (CREDIT, LIABILITY, PAYABLE) {@link #LIABILITY};
     * any other type is its own family.
     */
    public AccountType family() {
        return switch (this) {
            case BANK, CASH, STOCK, MUTUAL, ASSET, RECEIVABLE -> ASSET;
            case CREDIT, LIABILITY, PAYABLE -> LIABILITY;
            default -> this;
        };
    }

}
