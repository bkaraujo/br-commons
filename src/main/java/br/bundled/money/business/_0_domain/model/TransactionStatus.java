package br.bundled.money.business._0_domain.model;

public enum TransactionStatus {

    /** Transaction expected to occur at a {@link AccountNature#FINANCE_ACCOUNT}*/
    PLANNED,

    /** Transaction will occur at a {@link AccountNature#FINANCE_ACCOUNT} in a future date */
    CONFIRMED,

    /** Transaction conciliated in the {@link AccountNature#FINANCE_ACCOUNT} */
    CONCILIATED
}
