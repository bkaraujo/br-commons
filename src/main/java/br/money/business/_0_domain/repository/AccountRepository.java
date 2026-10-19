package br.money.business._0_domain.repository;

import br.money.business._0_domain.model.Account;
import br.money.business._0_domain.model.AccountNature;
import br.money.business._0_domain.model.AccountType;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.UUID;

@NullMarked
public interface AccountRepository {

    void save(Account account);
    void delete(Account account);

    List<Account> findAll();
    Account findById(UUID id);
    List<Account> findByNature(AccountNature nature);
    List<Account> findByType(AccountType type);


}
