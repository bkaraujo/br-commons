package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.module.Lifecycle;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The use cases over the repositories given (implemented outside, by the persistence), sharing one lock: the book is
 * changed by one use case at a time. Started after the repositories are loaded; an empty book (no accounts) starts
 * from the personal chart of accounts (INTENT F8).
 */
@NullMarked
public final class ApplicationModule implements Lifecycle {

    private final AccountRepository accountRepository;
    private final Balances balances;
    private final TransactionService transactions;
    private final AccountService accounts;
    private final BookService books;
    private final ImportService imports;
    private final Clock clock;

    public ApplicationModule(AccountRepository accounts, TransactionRepository transactions, BalanceRepository balances,
                             BookRepository books, Clock clock) {
        this.accountRepository = accounts;
        val lock = new ReentrantLock();
        this.balances = new Balances(accounts, transactions, balances, clock);
        this.transactions = new TransactionService(accounts, transactions, books, this.balances, lock);
        this.accounts = new AccountService(accounts, transactions, balances, books, this.balances, this.transactions, lock);
        this.imports = new ImportService(accounts, transactions, balances, books, this.balances, lock);
        this.books = new BookService(books, imports, lock);
        this.clock = clock;
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        return accountRepository.findAll().isEmpty() ? books.startEmpty().flatMap(_ -> Result.success()) : Result.success();
    }

    public Balances balances() { return balances; }
    public ImportService imports() { return imports; }

    /** Today, by the clock of the book (its time zone). */
    public LocalDate today() { return LocalDate.now(clock); }
    public TransactionService transactions() { return transactions; }
    public AccountService accounts() { return accounts; }
    public BookService books() { return books; }
}
