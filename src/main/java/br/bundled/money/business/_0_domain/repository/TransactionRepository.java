package br.bundled.money.business._0_domain.repository;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Transaction;
import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@NullMarked
public interface TransactionRepository {

    void save(Transaction transaction);
    void delete(Transaction transaction);

    List<Transaction> findByAccount(Account account);
    List<Transaction> findByAccountAndDate(Account account, LocalDate date);
    List<Transaction> findByAccountPeriod(Account account, YearMonth period);
    List<Transaction> findByPeriod(YearMonth period);
}
