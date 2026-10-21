package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.*;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * A whole book loaded at once (INTENT F7 import, F8 template): what was there is replaced. The chart comes as it is,
 * top level included; the rules of the screens (top level fixed, closed period) do not apply to what is loaded, but
 * every transaction must balance. Everything is built and checked before the current book is removed; when the storage
 * fails halfway, the previous book is written back. The balances kept are recalculated at the end.
 */
@NullMarked
public final class ImportService {

    /** A split to load: the position of its account in the chart loaded and the amount in the account's nature. */
    public record Line(int account, BigDecimal amount) {}

    /** A transaction to load. */
    public record Posting(LocalDate date, String description, List<Line> lines) {}

    /** The book as it was before a load, to write back when the load cannot be stored. */
    private record Snapshot(List<Account> accounts, List<Transaction> transactions, Book book) {}

    private final AccountRepository accounts = Context.get(AccountRepository.class);
    private final TransactionRepository transactions = Context.get(TransactionRepository.class);
    private final BalanceRepository kept = Context.get(BalanceRepository.class);
    private final BookRepository books = Context.get(BookRepository.class);
    private final Balances balances = Context.get(Balances.class);
    private final Lock lock = Context.get(BookLock.class);

    ImportService() {}

    /**
     * Replaces the book: the accounts of {@code chart} (parents before children), the transactions and the closing
     * date. Answers the accounts created, in the order of {@code chart}.
     */
    public Result<List<Account>, BusinessError> load(List<ChartTemplate.Entry> chart, List<Posting> postings,
                                                    @Nullable LocalDate threshold) {
        return Rules.locked(lock, () -> {
            val created = new ArrayList<Account>();
            for (val entry : chart) {
                val parent = entry.parent();
                if (parent != null && (parent < 0 || parent >= created.size())) {
                    return Result.failure(DomainErrors.validation(DomainErrors.IMPORT_ACCOUNT, parent));
                }
                val account = new Account();
                account.name(entry.name());
                account.type(entry.type());
                account.parent(parent == null ? null : created.get(parent));
                created.add(account);
            }
            val built = new ArrayList<BookTransaction>();
            for (val posting : postings) {
                val splits = new ArrayList<BookSplit>();
                for (val line : posting.lines()) {
                    if (line.account() < 0 || line.account() >= created.size()) {
                        return Result.failure(DomainErrors.validation(DomainErrors.IMPORT_ACCOUNT, line.account()));
                    }
                    splits.add(BookSplit.of(created.get(line.account()), line.amount()));
                }
                val transaction = BookTransaction.of(posting.date(), posting.description(), splits);
                transaction.validate();
                built.add(transaction);
            }
            val before = new Snapshot(accounts.findAll(), transactions.findAll(), books.find());
            val written = write(created, built, new Book(threshold));
            if (written.isFailure()) restore(before);
            return Rules.stored(written.map(_ -> List.copyOf(created)));
        });
    }

    /** The current book replaced by {@code chart} (parents before children), {@code postings} and {@code book}. */
    private Result<Void, String> write(List<Account> chart, List<? extends Transaction> postings, Book book) {
        val cleared = clear();
        if (cleared.isFailure()) return cleared;
        for (val account : chart) {
            val saved = accounts.save(account);
            if (saved.isFailure()) return saved.flatMap(_ -> Result.success());
        }
        for (val transaction : postings) {
            val saved = transactions.save(transaction);
            if (saved.isFailure()) return saved.flatMap(_ -> Result.success());
        }
        return books.save(book).flatMap(_ -> balances.rebuild());
    }

    /** The book of {@code before} written back over what a failed load left (best effort: the storage just failed). */
    private void restore(Snapshot before) {
        write(before.accounts().stream().sorted(Comparator.comparingInt(ImportService::depth)).toList(),
                before.transactions(), before.book());
    }

    private static int depth(Account account) {
        val parent = account.parent();
        return parent == null ? 0 : 1 + depth(parent);
    }

    /** Every transaction, balance kept and account removed. */
    private Result<Void, String> clear() {
        for (val transaction : transactions.findAll()) {
            val deleted = transactions.delete(transaction);
            if (deleted.isFailure()) return deleted;
        }
        for (val account : accounts.findAll()) {
            val deleted = kept.delete(account).flatMap(_ -> accounts.delete(account));
            if (deleted.isFailure()) return deleted;
        }
        return Result.success();
    }
}
