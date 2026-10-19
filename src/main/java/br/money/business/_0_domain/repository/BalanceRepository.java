package br.money.business._0_domain.repository;

import br.money.business._0_domain.model.Account;
import br.money.business._0_domain.model.Balance;
import org.jspecify.annotations.NullMarked;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;

@NullMarked
public interface BalanceRepository {

    List<Balance> findByAccount(Account account, Year year);
    Balance findByAccount(Account account, YearMonth period);

}
