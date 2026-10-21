package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.module.Lifecycle;
import org.jspecify.annotations.NullMarked;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The use cases over the repositories in the {@link Context} (implemented outside, by the persistence), sharing one
 * {@link BookLock}: the book is changed by one use case at a time. Creating it registers each use case, after the ones
 * it needs. Started after the repositories are loaded; an empty book (no accounts) starts from the personal chart of
 * accounts (INTENT F8).
 */
@NullMarked
public final class ApplicationModule implements Lifecycle {

    public ApplicationModule() {
        Context.set(BookLock.class);
        Context.set(Balances.class, Balances::new);
        Context.set(TransactionService.class, TransactionService::new);
        Context.set(AccountService.class, AccountService::new);
        Context.set(ImportService.class, ImportService::new);
        Context.set(BookService.class, BookService::new);
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        return Context.get(AccountRepository.class).findAll().isEmpty() ? books().startEmpty().flatMap(_ -> Result.success()) : Result.success();
    }

    public Balances balances() { return Context.get(Balances.class); }
    public ImportService imports() { return Context.get(ImportService.class); }

    /** Today, by the clock of the book (its time zone). */
    public LocalDate today() { return LocalDate.now(Context.get(Clock.class)); }
    public TransactionService transactions() { return Context.get(TransactionService.class); }
    public AccountService accounts() { return Context.get(AccountService.class); }
    public BookService books() { return Context.get(BookService.class); }
}
