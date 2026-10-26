package br.bundled.money.business._0_domain.model;

import br.bundled.money.business._0_domain.DomainErrors;
import br.commons.annotation.Entity;
import br.commons.business.BusinessException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@NullMarked
public abstract class Transaction extends Entity implements Auditable {

    public abstract LocalDate date();
    public int installment() { return 1; }
    public int installmentTotal() { return 1; }
    public abstract BigDecimal installmentAmount();

    /** Original purchase identity of an installment (GnuCash slot {@code br-money/purchase}); null when not an installment */
    public @Nullable UUID purchase() { return null; }

    /** Day the purchase was made, when it differs from {@link #date()} (a card purchase is posted on the bill's due date); null when unknown */
    public @Nullable LocalDate purchased() { return null; }

    /** What the user wrote about it, free text beyond the description; null when there is none */
    public @Nullable String note() { return null; }

    public abstract TransactionStatus status();

    public abstract List<TransactionSplit> splits();

    /** Σ (amount × type.debit) = 0 */
    public final boolean balanced() {
        return splits().stream()
                .map(TransactionSplit::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .signum() == 0;
    }

    /** Rejects with {@link BusinessException} when less than 2 splits or not {@link #balanced()} */
    public final void validate() {
        if (splits().size() < 2) throw new BusinessException(DomainErrors.rule(DomainErrors.TRANSACTION_SPLITS, identity()));
        if (!balanced()) throw new BusinessException(DomainErrors.rule(DomainErrors.TRANSACTION_UNBALANCED, identity()));
    }

}
