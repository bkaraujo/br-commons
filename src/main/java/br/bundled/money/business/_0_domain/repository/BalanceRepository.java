package br.bundled.money.business._0_domain.repository;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Balance;
import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * The balances kept as a cache: the balance at the end of each month in which the account moved, of its own splits
 * (not its subaccounts'), in its nature. A month without movement keeps the balance of the latest one before it. The
 * transactions are the source; whoever changes them recalculates from the month changed on.
 */
@NullMarked
public interface BalanceRepository {

    /** The months of the year with a balance kept, in order. */
    List<Balance> findByAccount(Account account, Year year);

    /** The balance at the end of {@code period}: the one kept for it or, failing that, for the latest month before. */
    Optional<Balance> findByAccount(Account account, YearMonth period);

    Result<Void, String> save(Account account, Balance balance);

    /** Drops the balances kept from {@code period} on. */
    Result<Void, String> deleteFrom(Account account, YearMonth period);

    /** Drops every balance kept of the account. */
    Result<Void, String> delete(Account account);

}
