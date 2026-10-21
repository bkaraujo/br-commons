package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Balance;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.commons.Result;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;

/** The balances kept in {@code balances-<account id>.json}, one field per month ({@code yyyy-MM}). */
@NullMarked
public final class StorageBalanceRepository implements BalanceRepository {

    static final String PREFIX = "balances-";
    static final String SUFFIX = ".json";

    record BalanceRow(String amount) {}

    private final Storage storage;
    private final Map<UUID, NavigableMap<YearMonth, BigDecimal>> balances = new HashMap<>();

    public StorageBalanceRepository(Storage storage) {
        this.storage = storage;
    }

    static String file(UUID account) {
        return PREFIX + account + SUFFIX;
    }

    public synchronized Result<Void, String> load() {
        balances.clear();
        for (val file : storage.listFiles(PREFIX, SUFFIX)) {
            final UUID account;
            try {
                account = UUID.fromString(file.substring(PREFIX.length(), file.length() - SUFFIX.length()));
            } catch (IllegalArgumentException e) {
                return Result.failure(Records.unreadable(file, file, String.valueOf(e.getMessage())));
            }
            val keys = storage.keys(file);
            if (keys instanceof Result.Failure<List<String>, String>(var error)) return Result.failure(error);
            for (val key : keys.getOrThrow()) {
                val read = Records.read(storage, file, key, BalanceRow.class);
                if (read instanceof Result.Failure<BalanceRow, String>(var error)) return Result.failure(error);
                try {
                    months(account).put(YearMonth.parse(key), new BigDecimal(read.getOrThrow().amount()));
                } catch (RuntimeException e) {
                    return Result.failure(Records.unreadable(key, file, String.valueOf(e.getMessage())));
                }
            }
        }
        return Result.success();
    }

    private NavigableMap<YearMonth, BigDecimal> months(UUID account) {
        return balances.computeIfAbsent(account, _ -> new TreeMap<>());
    }

    @Override
    public synchronized List<Balance> findByAccount(Account account, Year year) {
        return months(account.identity()).subMap(year.atMonth(1), true, year.atMonth(12), true).entrySet().stream()
                .map(entry -> new Balance(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public synchronized Optional<Balance> findByAccount(Account account, YearMonth period) {
        return Optional.ofNullable(months(account.identity()).floorEntry(period))
                .map(entry -> new Balance(period, entry.getValue()));
    }

    @Override
    public synchronized Result<Void, String> save(Account account, Balance balance) {
        return Records.write(storage, file(account.identity()), balance.period().toString(),
                        new BalanceRow(balance.amount().toPlainString()))
                .ifSuccess(_ -> months(account.identity()).put(balance.period(), balance.amount()));
    }

    @Override
    public synchronized Result<Void, String> deleteFrom(Account account, YearMonth period) {
        val months = months(account.identity());
        for (val month : List.copyOf(months.tailMap(period, true).keySet())) {
            val deleted = storage.delete(file(account.identity()), month.toString());
            if (deleted.isFailure()) return deleted;
            months.remove(month);
        }
        return Result.success();
    }

    @Override
    public synchronized Result<Void, String> delete(Account account) {
        return deleteFrom(account, YearMonth.of(0, 1)).ifSuccess(_ -> balances.remove(account.identity()));
    }
}
