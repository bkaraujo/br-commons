package br.bundled.money.business._0_domain.repository;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountNature;
import br.bundled.money.business._0_domain.model.AccountType;
import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The chart of accounts. Reads never fail (the implementation holds the accounts once loaded); writes answer the
 * storage's failure as text. The accounts found are the ones kept: change one and {@link #save} it.
 */
@NullMarked
public interface AccountRepository {

    Result<Account, String> save(Account account);
    Result<Void, String> delete(Account account);

    /** Every account, by name. */
    List<Account> findAll();
    Optional<Account> findById(UUID id);
    List<Account> findByNature(AccountNature nature);
    List<Account> findByType(AccountType type);

    /** The direct subaccounts, by name. */
    List<Account> findChildren(Account parent);

}
