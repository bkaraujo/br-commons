package br.money.business._0_domain.model;

import br.commons.annotation.Entity;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;

/** Split identity maps to the GnuCash split GUID (round-trip) */
@NullMarked
public abstract class TransactionSplit extends Entity implements Auditable {

    public abstract Account account();

    /** Signed from the account type point of view: positive increases its balance */
    public abstract BigDecimal amount();

    /** Amount in debit convention (GnuCash value): amount × type.debit */
    public final BigDecimal debitAmount() {
        return amount().multiply(BigDecimal.valueOf(account().type().debit));
    }

}
