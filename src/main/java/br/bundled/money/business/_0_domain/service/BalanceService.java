package br.bundled.money.business._0_domain.service;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Balance;
import org.jspecify.annotations.NullMarked;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;

@NullMarked
public interface BalanceService {

    /** Current YearMonth balance */
    Balance balance(Account account);

    /** Year-month specific balance */
    Balance balance(Account account, YearMonth period);

    /** Per-month balance of the year */
    List<Balance> balance(Account account, Year year);


}
