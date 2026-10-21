package br.bundled.money.business._0_domain.repository;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactions of the book. Reads never fail (the implementation holds them once loaded) and come by date, then
 * creation; writes answer the storage's failure as text. {@code findByAccount*}: the transactions with a split in the
 * account itself (not in its subaccounts).
 */
@NullMarked
public interface TransactionRepository {

    Result<Transaction, String> save(Transaction transaction);
    Result<Void, String> delete(Transaction transaction);

    Optional<Transaction> findById(UUID id);
    List<Transaction> findAll();
    List<Transaction> findByAccount(Account account);
    List<Transaction> findByAccountAndDate(Account account, LocalDate date);
    List<Transaction> findByAccountPeriod(Account account, YearMonth period);
    List<Transaction> findByPeriod(YearMonth period);
}
