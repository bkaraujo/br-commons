package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.Balance;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.bundled.money.business._0_domain.service.BalanceService;
import br.commons.Result;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The balances (INTENT §6) from the cache of {@link BalanceRepository}: a financial account shows its own balance; a
 * ledger account sums its subtree, each account converted to its nature ({@code own × debit of the account × debit
 * of the one asked}). The cache is kept by {@link #refresh}, called by whoever changes transactions.
 */
@NullMarked
public final class Balances implements BalanceService {

    private final AccountRepository accounts = Context.get(AccountRepository.class);
    private final TransactionRepository transactions = Context.get(TransactionRepository.class);
    private final BalanceRepository balances = Context.get(BalanceRepository.class);
    private final Clock clock = Context.get(Clock.class);

    Balances() {}

    @Override
    public Balance balance(Account account) {
        return balance(account, YearMonth.now(clock));
    }

    @Override
    public Balance balance(Account account, YearMonth period) {
        if (account.financial()) return new Balance(period, own(account, period));
        val debit = BigDecimal.valueOf(account.type().debit);
        val total = subtree(account).stream()
                .map(each -> own(each, period).multiply(BigDecimal.valueOf(each.type().debit)).multiply(debit))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Balance(period, total);
    }

    @Override
    public List<Balance> balance(Account account, Year year) {
        val months = new ArrayList<Balance>();
        for (var month = 1; month <= 12; month++) months.add(balance(account, year.atMonth(month)));
        return months;
    }

    /** The balance of the account's own splits at the end of {@code period}, in its nature. */
    public BigDecimal own(Account account, YearMonth period) {
        return balances.findByAccount(account, period).map(Balance::amount).orElse(BigDecimal.ZERO);
    }

    /** The account and every descendant. */
    public List<Account> subtree(Account account) {
        val all = new ArrayList<Account>();
        val pending = new ArrayDeque<>(List.of(account));
        while (!pending.isEmpty()) {
            val next = pending.pop();
            all.add(next);
            accounts.findChildren(next).forEach(pending::push);
        }
        return all;
    }

    /**
     * Recalculates the balances kept of each account from {@code from} on: the months in which it moved, each the
     * balance before plus what moved in its own splits.
     */
    Result<Void, String> refresh(Collection<Account> changed, YearMonth from) {
        for (val account : new LinkedHashSet<>(changed)) {
            val dropped = balances.deleteFrom(account, from);
            if (dropped.isFailure()) return dropped;
            var running = own(account, from.minusMonths(1));
            val flows = new TreeMap<>(transactions.findByAccount(account).stream()
                    .filter(transaction -> !YearMonth.from(transaction.date()).isBefore(from))
                    .collect(Collectors.groupingBy(transaction -> YearMonth.from(transaction.date()),
                            Collectors.reducing(BigDecimal.ZERO, transaction -> moved(transaction, account), BigDecimal::add))));
            for (val flow : flows.entrySet()) {
                running = running.add(flow.getValue());
                val saved = balances.save(account, new Balance(flow.getKey(), running));
                if (saved.isFailure()) return saved;
            }
        }
        return Result.success();
    }

    /** Recalculates every account from the first transaction of the book. */
    Result<Void, String> rebuild() {
        return transactions.findAll().stream().findFirst()
                .map(first -> refresh(accounts.findAll(), YearMonth.from(first.date())))
                .orElseGet(Result::success);
    }

    /** What the transaction moved in the account's own splits, in its nature. */
    private static BigDecimal moved(Transaction transaction, Account account) {
        return transaction.splits().stream().filter(split -> split.account().equals(account))
                .map(TransactionSplit::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
