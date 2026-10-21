package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.module.Lifecycle;
import br.commons.persistence.Storage;
import org.jspecify.annotations.NullMarked;

/**
 * The repositories over the {@link Storage} in the {@link Context}, each registered there by its interface (the
 * transactions after the accounts they resolve); {@link #initialize} loads them (accounts before the transactions that
 * point to them). A record that cannot be read back stops the startup.
 */
@NullMarked
public final class PersistenceModule implements Lifecycle {

    public PersistenceModule() {
        Context.set(AccountRepository.class, StorageAccountRepository::new);
        Context.set(TransactionRepository.class, StorageTransactionRepository::new);
        Context.set(BalanceRepository.class, StorageBalanceRepository::new);
        Context.set(BookRepository.class, StorageBookRepository::new);
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        return ((StorageAccountRepository) Context.get(AccountRepository.class)).load()
                .flatMap(_ -> ((StorageTransactionRepository) Context.get(TransactionRepository.class)).load())
                .flatMap(_ -> ((StorageBalanceRepository) Context.get(BalanceRepository.class)).load())
                .flatMap(_ -> ((StorageBookRepository) Context.get(BookRepository.class)).load())
                .mapFailure(BusinessError.Infrastructure::new);
    }

}
