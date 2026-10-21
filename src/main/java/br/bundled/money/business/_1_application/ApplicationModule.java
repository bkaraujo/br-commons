package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.service.BalanceService;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.module.Lifecycle;
import org.jspecify.annotations.NullMarked;


/**
 * The use cases over the repositories in the {@link Context} (implemented outside, by the persistence), sharing one
 * {@link BookLock}: the book is changed by one use case at a time. Creating it registers each use case, after the ones
 * it needs ({@link Balances} also as the {@link BalanceService} of the domain). Started after the repositories are loaded; an empty book (no accounts) starts from the personal chart of
 * accounts (INTENT F8).
 */
@NullMarked
public final class ApplicationModule implements Lifecycle {

    public ApplicationModule() {
        Context.set(BookLock.class);
        Context.set(Balances.class, Balances::new);
        Context.set(BalanceService.class, () -> Context.get(Balances.class));
        Context.set(TransactionService.class, TransactionService::new);
        Context.set(AccountService.class, AccountService::new);
        Context.set(ImportService.class, ImportService::new);
        Context.set(BookService.class, BookService::new);
    }

    @Override
    public Result<Void, BusinessError> initialize() {
        return Context.get(AccountRepository.class).findAll().isEmpty() ?
                Context.get(BookService.class).startEmpty().flatMap(_ -> Result.success()) :
                Result.success();
    }
}
