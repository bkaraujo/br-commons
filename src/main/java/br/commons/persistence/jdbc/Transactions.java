package br.commons.persistence.jdbc;

import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

/**
 * Transaction demarcation for use cases, without SQL (spec §2.4). The work joins the transaction already
 * open on the thread (propagation REQUIRED) or opens one; only the outermost level commits. A
 * {@link Result.Failure} or an exception marks the transaction rollback-only.
 */
@NullMarked
public interface Transactions {

    <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work);

    /**
     * Runs {@code action} after the outermost transaction of this thread commits; discarded when it rolls
     * back. Without a transaction on the thread, runs at once.
     */
    void afterCommit(Runnable action);
}
