package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.*;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Predicate;

/**
 * The transactions in {@code transactions-<yyyy-MM>.json}, the file of the month of their date, one field per id; the
 * splits inside, each account by its id (resolved by the {@link AccountRepository}, loaded first). A transaction whose
 * date moves to another month moves to that file.
 */
@NullMarked
public final class StorageTransactionRepository implements TransactionRepository {

    static final String PREFIX = "transactions-";
    static final String SUFFIX = ".json";

    record SplitRow(UUID id, UUID account, String amount, long createdAt, long updatedAt) {}

    record TransactionRow(UUID id, String date, String description, TransactionStatus status, int installment,
                          int installmentTotal, String installmentAmount, @Nullable UUID purchase, List<SplitRow> splits,
                          long createdAt, long updatedAt) {}

    private static final Comparator<Transaction> CHRONOLOGICAL = Comparator.comparing(Transaction::date)
            .thenComparingLong(Transaction::createdAt).thenComparing(Comparator.naturalOrder());

    private final Storage storage = Context.get(Storage.class);
    private final AccountRepository accounts = Context.get(AccountRepository.class);
    private final Map<UUID, Transaction> transactions = new HashMap<>();

    static String file(YearMonth month) {
        return PREFIX + month + SUFFIX;
    }

    /** Reads every month's file; a split whose account is not in the chart fails the load. */
    public synchronized Result<Void, String> load() {
        transactions.clear();
        for (val file : storage.listFiles(PREFIX, SUFFIX)) {
            val keys = storage.keys(file);
            if (keys instanceof Result.Failure<List<String>, String>(var error)) return Result.failure(error);
            for (val key : keys.getOrThrow()) {
                val read = Records.read(storage, file, key, TransactionRow.class).flatMap(row -> transaction(row, file));
                if (read instanceof Result.Failure<BookTransaction, String>(var error)) return Result.failure(error);
                val transaction = read.getOrThrow();
                transactions.put(transaction.identity(), transaction);
            }
        }
        return Result.success();
    }

    /** The row as a transaction; a text that is not a date or an amount makes the row unreadable. */
    private Result<BookTransaction, String> transaction(TransactionRow row, String file) {
        try {
            return parse(row, file);
        } catch (RuntimeException e) {
            return Result.failure(Records.unreadable(row.id().toString(), file, String.valueOf(e.getMessage())));
        }
    }

    private Result<BookTransaction, String> parse(TransactionRow row, String file) {
        val splits = new ArrayList<BookSplit>();
        for (val split : row.splits()) {
            val account = accounts.findById(split.account());
            if (account.isEmpty()) return Result.failure(Records.unreadable(row.id().toString(), file, "account " + split.account()));
            splits.add(new BookSplit(split.id(), account.get(), new BigDecimal(split.amount()), split.createdAt(), split.updatedAt()));
        }
        val details = new BookTransaction.Details(row.status(), row.installment(), row.installmentTotal(),
                new BigDecimal(row.installmentAmount()), row.purchase());
        return Result.success(new BookTransaction(row.id(), LocalDate.parse(row.date()), row.description(), splits, details,
                row.createdAt(), row.updatedAt()));
    }

    private static TransactionRow row(Transaction transaction) {
        val splits = transaction.splits().stream().map(split -> new SplitRow(split.identity(), split.account().identity(),
                split.amount().toPlainString(), split.createdAt(), split.updatedAt())).toList();
        return new TransactionRow(transaction.identity(), transaction.date().toString(), transaction.description(),
                transaction.status(), transaction.installment(), transaction.installmentTotal(),
                transaction.installmentAmount().toPlainString(), transaction.purchase(), splits,
                transaction.createdAt(), transaction.updatedAt());
    }

    /** Written in the file of its month; then, if the date moved from another month, removed from that one. */
    @Override
    public synchronized Result<Transaction, String> save(Transaction transaction) {
        val key = transaction.identity().toString();
        val previous = transactions.get(transaction.identity());
        val month = YearMonth.from(transaction.date());
        var written = Records.write(storage, file(month), key, row(transaction));
        if (previous != null && !YearMonth.from(previous.date()).equals(month)) {
            written = written.flatMap(_ -> storage.delete(file(YearMonth.from(previous.date())), key));
        }
        return written.map(_ -> {
            transactions.put(transaction.identity(), transaction);
            return transaction;
        });
    }

    @Override
    public synchronized Result<Void, String> delete(Transaction transaction) {
        val kept = transactions.getOrDefault(transaction.identity(), transaction);
        return storage.delete(file(YearMonth.from(kept.date())), transaction.identity().toString())
                .ifSuccess(_ -> transactions.remove(transaction.identity()));
    }

    @Override
    public synchronized Optional<Transaction> findById(UUID id) {
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public synchronized List<Transaction> findAll() {
        return where(_ -> true);
    }

    @Override
    public synchronized List<Transaction> findByAccount(Account account) {
        return where(touching(account));
    }

    @Override
    public synchronized List<Transaction> findByAccountAndDate(Account account, LocalDate date) {
        return where(touching(account).and(transaction -> transaction.date().equals(date)));
    }

    @Override
    public synchronized List<Transaction> findByAccountPeriod(Account account, YearMonth period) {
        return where(touching(account).and(in(period)));
    }

    @Override
    public synchronized List<Transaction> findByPeriod(YearMonth period) {
        return where(in(period));
    }

    private List<Transaction> where(Predicate<Transaction> filter) {
        return transactions.values().stream().filter(filter).sorted(CHRONOLOGICAL).toList();
    }

    private static Predicate<Transaction> touching(Account account) {
        return transaction -> transaction.splits().stream().anyMatch(split -> split.account().equals(account));
    }

    private static Predicate<Transaction> in(YearMonth period) {
        return transaction -> YearMonth.from(transaction.date()).equals(period);
    }
}
