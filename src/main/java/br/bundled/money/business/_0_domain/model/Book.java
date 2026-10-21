package br.bundled.money.business._0_domain.model;

import br.bundled.money.business._0_domain.DomainErrors;
import br.commons.business.BusinessException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * What the book keeps besides accounts and transactions: the closing date (INTENT F9, GnuCash's read-only threshold).
 * Every transaction dated before it is immutable; {@code null}, nothing is closed.
 */
@NullMarked
public record Book(@Nullable LocalDate threshold) {

    public static final Book OPEN = new Book(null);

    public boolean closed(LocalDate date) {
        return threshold != null && date.isBefore(threshold);
    }

    /**
     * Rejects a change dated in the closed period with {@link BusinessException} ({@link DomainErrors#PERIOD_CLOSED});
     * {@code what} names what was changed in the message.
     */
    public void open(LocalDate date, Object what) {
        if (threshold != null && closed(date)) {
            throw new BusinessException(DomainErrors.rule(DomainErrors.PERIOD_CLOSED, what, threshold));
        }
    }
}
