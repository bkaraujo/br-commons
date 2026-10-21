package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.*;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

/**
 * Posting, revising and discarding transactions (INTENT F2, F9): at least two splits, balanced, amounts of up to 2
 * decimals (zero splits are dropped), dated in the open period, the old date too when revising or discarding. Every
 * change recalculates the balances kept of the accounts touched.
 */
@NullMarked
public final class TransactionService {

    private final AccountRepository accounts = Context.get(AccountRepository.class);
    private final TransactionRepository transactions = Context.get(TransactionRepository.class);
    private final BookRepository books = Context.get(BookRepository.class);
    private final Balances balances = Context.get(Balances.class);
    private final Lock lock = Context.get(BookLock.class);

    TransactionService() {}

    public Optional<Transaction> find(UUID id) {
        return transactions.findById(id);
    }

    /** Every transaction, by date. */
    public List<Transaction> all() {
        return transactions.findAll();
    }

    /** The transactions dated in the month, by date. */
    public List<Transaction> month(YearMonth period) {
        return transactions.findByPeriod(period);
    }

    /** The transactions with a split in the account itself during the month, by date. */
    public List<Transaction> statement(Account account, YearMonth period) {
        return transactions.findByAccountPeriod(account, period);
    }

    /** A complete transaction: any number of splits (INTENT F2). */
    public Result<Transaction, BusinessError> post(LocalDate date, String description, List<SplitInput> splits) {
        return Rules.locked(lock, () -> {
            val built = splits(splits);
            if (built instanceof Result.Failure<List<BookSplit>, BusinessError>(var error)) return Result.failure(error);
            return save(BookTransaction.of(date, description.strip(), built.getOrThrow()), null);
        });
    }

    /**
     * {@code value} goes from {@code from} to {@code to}: it enters {@code to} and leaves {@code from}, each split in
     * the nature of its account (an expense paid by card: from the card to the expense).
     */
    public Result<Transaction, BusinessError> transfer(LocalDate date, String description, UUID from, UUID to,
                                                       BigDecimal value) {
        if (value.signum() <= 0) return Result.failure(DomainErrors.validation(DomainErrors.AMOUNT_POSITIVE, value));
        return Rules.locked(lock, () -> {
            val origin = accounts.findById(from);
            val destination = accounts.findById(to);
            if (origin.isEmpty()) return Result.failure(missing(from));
            if (destination.isEmpty()) return Result.failure(missing(to));
            val debit = BigDecimal.valueOf(destination.get().type().debit);
            val credit = BigDecimal.valueOf(origin.get().type().debit);
            return post(date, description, List.of(new SplitInput(to, value.multiply(debit)),
                    new SplitInput(from, value.negate().multiply(credit))));
        });
    }

    /** The transaction rewritten in place, same identity; each split in the same account keeps its identity too. */
    public Result<Transaction, BusinessError> revise(UUID id, LocalDate date, String description, List<SplitInput> splits) {
        return Rules.locked(lock, () -> {
            val found = transactions.findById(id);
            if (found.isEmpty()) return Result.failure(DomainErrors.notFound(DomainErrors.TRANSACTION_NOT_FOUND, id));
            books.find().open(found.get().date(), found.get().date());
            val built = splits(splits);
            if (built instanceof Result.Failure<List<BookSplit>, BusinessError>(var error)) return Result.failure(error);
            return save(Rules.revised(found.get(), date, description.strip(), keeping(found.get(), built.getOrThrow())),
                    found.get());
        });
    }

    public Result<Void, BusinessError> discard(UUID id) {
        return Rules.locked(lock, () -> {
            val found = transactions.findById(id);
            if (found.isEmpty()) return Result.failure(DomainErrors.notFound(DomainErrors.TRANSACTION_NOT_FOUND, id));
            val transaction = found.get();
            books.find().open(transaction.date(), transaction.date());
            return Rules.stored(transactions.delete(transaction)
                    .flatMap(_ -> balances.refresh(accounts(transaction), YearMonth.from(transaction.date()))));
        });
    }

    /**
     * Checks and saves {@code transaction}, which replaces {@code previous} (null: a new one), then recalculates the
     * balances of the accounts of both from the earlier month.
     */
    Result<Transaction, BusinessError> save(BookTransaction transaction, @Nullable Transaction previous) {
        if (transaction.description().isBlank()) {
            return Result.failure(DomainErrors.validation(DomainErrors.TRANSACTION_DESCRIPTION));
        }
        books.find().open(transaction.date(), transaction.date());
        transaction.validate();
        val touched = new ArrayList<>(accounts(transaction));
        var from = YearMonth.from(transaction.date());
        if (previous != null) {
            touched.addAll(accounts(previous));
            from = min(from, YearMonth.from(previous.date()));
        }
        val month = from;
        return Rules.stored(transactions.save(transaction)
                .flatMap(saved -> balances.refresh(touched, month).map(_ -> saved)));
    }

    /** The splits with their accounts, amounts of up to 2 decimals; the zero ones dropped. */
    private Result<List<BookSplit>, BusinessError> splits(List<SplitInput> inputs) {
        val splits = new ArrayList<BookSplit>();
        for (val input : inputs) {
            if (input.amount().stripTrailingZeros().scale() > 2) {
                return Result.failure(DomainErrors.validation(DomainErrors.AMOUNT_SCALE, input.amount()));
            }
            val account = accounts.findById(input.account());
            if (account.isEmpty()) return Result.failure(missing(input.account()));
            if (input.amount().signum() != 0) {
                splits.add(BookSplit.of(account.get(), input.amount().setScale(2, RoundingMode.UNNECESSARY)));
            }
        }
        return Result.success(splits);
    }

    private static BusinessError missing(UUID account) {
        return DomainErrors.notFound(DomainErrors.ACCOUNT_NOT_FOUND, account);
    }

    /**
     * {@code splits} with the identity and creation of the previous splits of the same account, in order: a split
     * edited stays the same split (its GUID in the GnuCash round-trip); one in a new account is new.
     */
    private static List<BookSplit> keeping(Transaction previous, List<BookSplit> splits) {
        val unused = new ArrayList<>(previous.splits());
        val kept = new ArrayList<BookSplit>();
        for (val split : splits) {
            val same = unused.stream().filter(old -> old.account().equals(split.account())).findFirst();
            same.ifPresent(unused::remove);
            kept.add(same.map(old -> new BookSplit(old.identity(), split.account(), split.amount(), old.createdAt(),
                    System.currentTimeMillis())).orElse(split));
        }
        return kept;
    }

    private static List<Account> accounts(Transaction transaction) {
        return transaction.splits().stream().map(TransactionSplit::account).distinct().toList();
    }

    private static YearMonth min(YearMonth first, YearMonth second) {
        return first.isBefore(second) ? first : second;
    }
}
