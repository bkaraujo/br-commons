package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Book;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/** The book as a whole: its closing date (INTENT F9) and starting over from the personal chart of accounts (F8). */
@NullMarked
public final class BookService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BalanceRepository kept;
    private final BookRepository books;
    private final Lock lock;

    BookService(AccountRepository accounts, TransactionRepository transactions, BalanceRepository kept,
                BookRepository books, Lock lock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.kept = kept;
        this.books = books;
        this.lock = lock;
    }

    public Book book() {
        return books.find();
    }

    /**
     * Closes the period before {@code date}; moving it back (or {@code null}) is the explicit reopening, which the
     * screen confirms: correct, then move it forward again.
     */
    public Result<Book, BusinessError> close(@Nullable LocalDate date) {
        return Rules.locked(lock, () -> Rules.stored(books.save(new Book(date))));
    }

    /**
     * Starts over (INTENT F8): every transaction, balance and account removed, the accounts of the personal chart
     * created, nothing closed. Answers the accounts created.
     */
    public Result<List<Account>, BusinessError> startEmpty() {
        return Rules.locked(lock, () -> {
            for (val transaction : transactions.findAll()) {
                val deleted = transactions.delete(transaction);
                if (deleted.isFailure()) return Rules.stored(deleted.map(_ -> List.of()));
            }
            for (val account : accounts.findAll()) {
                val deleted = kept.delete(account).flatMap(_ -> accounts.delete(account));
                if (deleted.isFailure()) return Rules.stored(deleted.map(_ -> List.of()));
            }
            val created = new ArrayList<Account>();
            for (val entry : ChartTemplate.personal()) {
                val account = new Account();
                account.name(entry.name());
                account.type(entry.type());
                val parent = entry.parent();
                account.parent(parent == null ? null : created.get(parent));
                val saved = accounts.save(account);
                if (saved.isFailure()) return Rules.stored(saved.map(_ -> List.of()));
                created.add(account);
            }
            return Rules.stored(books.save(Book.OPEN).map(_ -> List.copyOf(created)));
        });
    }
}
