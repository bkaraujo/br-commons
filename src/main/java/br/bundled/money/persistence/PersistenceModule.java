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

    private final StorageAccountRepository accounts;
    private final StorageTransactionRepository transactions;
    private final StorageBalanceRepository balances;
    private final StorageBookRepository books;

    public PersistenceModule() {
        accounts = new StorageAccountRepository();
        Context.set(AccountRepository.class, this::accounts);
        transactions = new StorageTransactionRepository();
        Context.set(TransactionRepository.class, this::transactions);
        balances = new StorageBalanceRepository();
        Context.set(BalanceRepository.class, this::balances);
        books = new StorageBookRepository();
        Context.set(BookRepository.class, this::books);
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        return accounts.load()
                .flatMap(_ -> transactions.load())
                .flatMap(_ -> balances.load())
                .flatMap(_ -> books.load())
                .mapFailure(BusinessError.Infrastructure::new);
    }

    public AccountRepository accounts() { return accounts; }
    public TransactionRepository transactions() { return transactions; }
    public BalanceRepository balances() { return balances; }
    public BookRepository books() { return books; }
}
