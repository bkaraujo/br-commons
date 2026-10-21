package br.bundled.money.business._1_application;

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
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * A whole book loaded at once (INTENT F7 import, F8 template): what was there is replaced. The chart comes as it is,
 * top level included; the rules of the screens (top level fixed, closed period) do not apply to what is loaded, but
 * every transaction must balance. The balances kept are recalculated at the end.
 */
@NullMarked
public final class ImportService {

    /** A split to load: the position of its account in the chart loaded and the amount in the account's nature. */
    public record Line(int account, BigDecimal amount) {}

    /** A transaction to load. */
    public record Posting(LocalDate date, String description, List<Line> lines) {}

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
            val cleared = clear();
            if (cleared.isFailure()) return Rules.stored(cleared.map(_ -> List.of()));
            val created = new ArrayList<Account>();
            for (val entry : chart) {
                val account = new Account();
                account.name(entry.name());
                account.type(entry.type());
                val parent = entry.parent();
                account.parent(parent == null ? null : created.get(parent));
                val saved = accounts.save(account);
                if (saved.isFailure()) return Rules.stored(saved.map(_ -> List.of()));
                created.add(account);
            }
            for (val posting : postings) {
                val splits = posting.lines().stream()
                        .map(line -> BookSplit.of(created.get(line.account()), line.amount())).toList();
                val transaction = BookTransaction.of(posting.date(), posting.description(), splits);
                transaction.validate();
                val saved = transactions.save(transaction);
                if (saved.isFailure()) return Rules.stored(saved.map(_ -> List.of()));
            }
            return Rules.stored(books.save(new Book(threshold)).flatMap(_ -> balances.rebuild())
                    .map(_ -> List.copyOf(created)));
        });
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
