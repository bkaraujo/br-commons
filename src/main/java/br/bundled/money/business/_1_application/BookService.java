package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Book;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.locks.Lock;

/** The book as a whole: its closing date (INTENT F9) and starting over from the personal chart of accounts (F8). */
@NullMarked
public final class BookService {

    private final BookRepository books = Context.get(BookRepository.class);
    private final ImportService imports = Context.get(ImportService.class);
    private final Lock lock = Context.get(BookLock.class);

    BookService() {}

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
        return imports.load(ChartTemplate.personal(), List.of(), null);
    }
}
