package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.model.BookTransaction;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
import br.commons.Result;
import br.commons.Threads;
import br.commons.business.BusinessError;
import br.commons.business.BusinessException;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/** What the use cases share: the lock of the book, a broken rule as a failure, a storage failure as a failure. */
@NullMarked
final class Rules {

    private Rules() {}

    /** The use case under the lock of the book; a {@link BusinessException} of the domain is its failure. */
    static <T> Result<T, BusinessError> locked(Lock lock, Supplier<Result<T, BusinessError>> work) {
        return Threads.locked(lock, () -> {
            try {
                return work.get();
            } catch (BusinessException e) {
                return Result.failure(e.getError());
            }
        });
    }

    /** A storage failure (text already localized) as {@link BusinessError.Infrastructure}. */
    static <T> Result<T, BusinessError> stored(Result<T, String> result) {
        return result.mapFailure(BusinessError.Infrastructure::new);
    }

    /** The transaction with new date, description and splits, same identity ({@link BookTransaction#revised}). */
    static BookTransaction revised(Transaction transaction, LocalDate date, String description,
                                   List<? extends TransactionSplit> splits) {
        return revised(transaction, date, description, splits, transaction.note());
    }

    /** {@link #revised(Transaction, LocalDate, String, List)} with a new note (null or blank: none). */
    static BookTransaction revised(Transaction transaction, LocalDate date, String description,
                                   List<? extends TransactionSplit> splits, @Nullable String note) {
        if (transaction instanceof BookTransaction kept) return kept.revised(date, description, splits, note);
        val details = new BookTransaction.Details(transaction.status(), transaction.installment(),
                transaction.installmentTotal(), transaction.installmentAmount(), transaction.purchase(), transaction.purchased(),
                note, transaction.pdfName());
        return new BookTransaction(transaction.identity(), date, description, splits, details, transaction.createdAt(),
                System.currentTimeMillis());
    }
}
