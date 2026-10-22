package br.bundled.money.web.book;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.BookTransaction;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
import br.bundled.money.business._1_application.*;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The book as the screens see it (INTENT §6): a ledger account sums its descendants, each split converted to its
 * nature; a financial account shows only its own balance. Reads come from the services; changes go to them and
 * answer their {@code Result}.
 */
@NullMarked
public final class Book {

    private static final String PATH_SEPARATOR = " › ";

    private final AccountService accountService = Context.get(AccountService.class);
    private final TransactionService transactionService = Context.get(TransactionService.class);
    private final BookService bookService = Context.get(BookService.class);
    private final Balances balanceService = Context.get(Balances.class);
    private final Clock clock = Context.get(Clock.class);

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * The month the screens open on: the one of the latest transaction up to today, else today's. An imported book
     * can stop months in the past; opening on an empty month would hide it.
     */
    public YearMonth month() {
        val today = today();
        return transactions().stream().map(Transaction::date).filter(date -> !date.isAfter(today))
                .max(Comparator.naturalOrder()).map(YearMonth::from).orElseGet(() -> YearMonth.from(today));
    }

    private List<Transaction> transactions() {
        return transactionService.all();
    }

    /** The month of the earliest transaction, else today's: where the month lists start. */
    public YearMonth firstMonth() {
        return transactions().stream().map(Transaction::date).min(Comparator.naturalOrder()).map(YearMonth::from)
                .orElseGet(() -> YearMonth.from(today()));
    }

    // ---------- closing (INTENT F9)

    public @Nullable LocalDate threshold() {
        return bookService.book().threshold();
    }

    public boolean closed(LocalDate date) {
        return bookService.book().closed(date);
    }

    public boolean closed(Transaction transaction) {
        return closed(transaction.date());
    }

    /** Whether the account has a transaction in the closed period: moving its splits away would change it. */
    public boolean closedUses(Account account) {
        return transactionService.all().stream().anyMatch(transaction -> closed(transaction) && touches(transaction, Set.of(account)));
    }

    public Result<?, BusinessError> close(@Nullable LocalDate date) {
        return bookService.close(date);
    }

    /** Starts over from the personal chart of accounts (INTENT F8). */
    public Result<?, BusinessError> startEmpty() {
        return bookService.startEmpty();
    }

    // ---------- accounts

    public Optional<Account> account(@Nullable UUID id) {
        return id == null ? Optional.empty() : accountService.account(id);
    }

    public List<Account> roots() {
        return accountService.accounts().stream().filter(account -> account.parent() == null).toList();
    }

    public List<Account> children(Account account) {
        return accountService.children(account);
    }

    /** Every account, depth first, children by name. */
    public List<Account> tree() {
        return chart().tree();
    }

    /** The accounts read once: depth first, and the children of each one that has any, by name. */
    private record Chart(List<Account> tree, Map<Account, List<Account>> children) {}

    /** {@link Chart} from one read of the accounts (by name): no search per account. */
    private Chart chart() {
        val accounts = accountService.accounts();
        val children = new HashMap<Account, List<Account>>();
        for (val account : accounts) {
            val parent = account.parent();
            if (parent != null) children.computeIfAbsent(parent, _ -> new ArrayList<>()).add(account);
        }
        val all = new ArrayList<Account>();
        accounts.stream().filter(account -> account.parent() == null).forEach(root -> collect(root, children, all));
        return new Chart(all, children);
    }

    private static void collect(Account account, Map<Account, List<Account>> children, List<Account> all) {
        all.add(account);
        children.getOrDefault(account, List.of()).forEach(child -> collect(child, children, all));
    }

    public int depth(Account account) {
        val parent = account.parent();
        return parent == null ? 0 : 1 + depth(parent);
    }

    /** {@code Despesas › Alimentação › Mercado}. */
    public String path(Account account) {
        val parent = account.parent();
        return parent == null ? account.name() : path(parent) + PATH_SEPARATOR + account.name();
    }

    public List<Account> financial() {
        return tree().stream().filter(Account::financial).toList();
    }

    /**
     * The financial accounts the screens offer ("Minhas contas", the quick entry): stocks and funds have no UI in the
     * MVP (INTENT §5).
     */
    public List<Account> moneyAccounts() {
        return financial().stream()
                .filter(account -> account.type() != AccountType.STOCK && account.type() != AccountType.MUTUAL).toList();
    }

    /** A credit card: a financial account of type {@link AccountType#CREDIT}. */
    public static boolean card(Account account) {
        return account.type() == AccountType.CREDIT;
    }

    /** A category of the quick entry: income or expense. */
    public static boolean category(Account account) {
        return account.type() == AccountType.INCOME || account.type() == AccountType.EXPENSE;
    }

    /** Income and expense accounts without children, the ones a quick entry can use. */
    public List<Account> categories() {
        val chart = chart();
        return chart.tree().stream().filter(Book::category).filter(account -> !chart.children().containsKey(account)).toList();
    }

    public Optional<Account> root(AccountType type) {
        return roots().stream().filter(account -> account.type() == type).findFirst();
    }

    public Set<Account> subtree(Account account) {
        return new HashSet<>(balanceService.subtree(account));
    }

    /** The accounts a balance or a ledger of {@code account} covers: itself when financial, its subtree otherwise. */
    private Set<Account> scope(Account account) {
        return account.financial() ? Set.of(account) : subtree(account);
    }

    /** The parents an account of {@code type} may have (the same family); when editing, never itself or a descendant. */
    public List<Account> parents(AccountType type, @Nullable Account editing) {
        val excluded = editing == null ? Set.<Account>of() : subtree(editing);
        return tree().stream().filter(account -> !excluded.contains(account))
                .filter(account -> account.type().family() == type.family()).toList();
    }

    /** How many transactions touch the account itself. */
    public long uses(Account account) {
        return accountService.uses(account);
    }

    /** Whether the type is fixed: the account has transactions, or subaccounts that must stay in its family. */
    public boolean fixedType(Account account) {
        return uses(account) > 0 || !children(account).isEmpty();
    }

    /** Where the transactions of a deleted account may go: any account but the top level and the account itself. */
    public List<Account> targets(Account account) {
        return tree().stream().filter(candidate -> candidate.parent() != null && !candidate.equals(account)).toList();
    }

    public Result<Account, BusinessError> create(AccountInput input) {
        return accountService.create(input, today());
    }

    public Result<Account, BusinessError> update(Account account, AccountInput input) {
        return accountService.update(account.identity(), input);
    }

    public Result<Void, BusinessError> remove(Account account, @Nullable Account target) {
        return accountService.remove(account.identity(), target == null ? null : target.identity());
    }

    // ---------- balances

    /**
     * The balance of the account's own splits at the end of {@code day}, in its nature: the month before from the
     * balances kept, plus what moved in the month up to the day.
     */
    private BigDecimal own(Account account, LocalDate day) {
        val month = YearMonth.from(day);
        val before = balanceService.own(account, month.minusMonths(1));
        return transactionService.statement(account, month).stream().filter(transaction -> !transaction.date().isAfter(day))
                .map(transaction -> amountIn(transaction, Set.of(account), account.type().debit))
                .reduce(before, BigDecimal::add);
    }

    /** Balance at the end of {@code day}, in the nature of the account (see {@link #scope}). */
    public BigDecimal balance(Account account, LocalDate day) {
        val debit = account.type().debit;
        return scope(account).stream()
                .map(each -> own(each, day).multiply(BigDecimal.valueOf((long) each.type().debit * debit)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal balance(Account account) {
        return balance(account, today());
    }

    /** {@link #balance} of every account at the end of {@code day}. */
    public Map<Account, BigDecimal> balances(LocalDate day) {
        val tree = tree();
        val own = new HashMap<Account, BigDecimal>();
        tree.forEach(account -> own.put(account, own(account, day).multiply(BigDecimal.valueOf(account.type().debit))));
        return inNature(tree, own);
    }

    public Map<Account, BigDecimal> balances() {
        return balances(today());
    }

    /** {@link #balance} converted to the nature of {@code type}: an account of the other nature is negative. */
    public BigDecimal balanceAs(Account account, LocalDate day, AccountType type) {
        return balance(account, day).multiply(BigDecimal.valueOf((long) account.type().debit * type.debit));
    }

    /** What moved in the account during the month (see {@link #scope}). */
    public BigDecimal flow(Account account, YearMonth month) {
        val accounts = scope(account);
        return transactionService.month(month).stream()
                .map(transaction -> amountIn(transaction, accounts, account.type().debit))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@link #flow} of every account during the month, in one pass over its transactions. */
    public Map<Account, BigDecimal> flows(YearMonth month) {
        val own = new HashMap<Account, BigDecimal>();
        transactionService.month(month).forEach(transaction -> transaction.splits()
                .forEach(split -> own.merge(split.account(), split.debitAmount(), BigDecimal::add)));
        return inNature(tree(), own);
    }

    /**
     * Each account's {@link #scope} summed from {@code debits} (own amounts in debit terms), in its nature. One pass
     * over the {@link #tree} backwards: a subtree is summed before its parent, which adds it to its own.
     */
    private static Map<Account, BigDecimal> inNature(List<Account> tree, Map<Account, BigDecimal> debits) {
        val sums = new HashMap<Account, BigDecimal>();
        val result = new HashMap<Account, BigDecimal>();
        for (val account : tree.reversed()) {
            val own = debits.getOrDefault(account, BigDecimal.ZERO);
            val sum = sums.getOrDefault(account, BigDecimal.ZERO).add(own);
            val parent = account.parent();
            if (parent != null) sums.merge(parent, sum, BigDecimal::add);
            result.put(account, (account.financial() ? own : sum).multiply(BigDecimal.valueOf(account.type().debit)));
        }
        return result;
    }

    /**
     * What the categories of {@code type} (income or expense) took in during the month, in its nature, counting only
     * the transactions that touch one of {@code accounts}: a transfer between them adds nothing.
     */
    public BigDecimal categoryFlow(Set<Account> accounts, YearMonth month, AccountType type) {
        val categories = accountService.accounts().stream().filter(account -> account.type() == type)
                .collect(Collectors.toSet());
        return transactionService.month(month).stream().filter(transaction -> touches(transaction, accounts))
                .map(transaction -> amountIn(transaction, categories, type.debit)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The month of an account: each transaction with the running balance, starting from the month's opening. */
    public List<LedgerLine> ledger(Account account, YearMonth month) {
        val accounts = scope(account);
        val debit = account.type().debit;
        var balance = balance(account, month.atDay(1).minusDays(1));
        val lines = new ArrayList<LedgerLine>();
        for (val transaction : transactionService.month(month)) {
            if (!touches(transaction, accounts)) continue;
            val amount = amountIn(transaction, accounts, debit);
            balance = balance.add(amount);
            lines.add(new LedgerLine(transaction, amount, balance, counterpart(transaction, accounts)));
        }
        return lines;
    }

    // ---------- transactions

    public int count() {
        return transactions().size();
    }

    public List<Transaction> recent(int limit) {
        return transactions().reversed().stream().limit(limit).toList();
    }

    public Optional<Transaction> transaction(@Nullable UUID id) {
        return id == null ? Optional.empty() : transactionService.find(id);
    }

    /** The effect of a transaction on the net worth: income positive, expense negative, transfers zero. */
    public BigDecimal effect(Transaction transaction) {
        return transaction.splits().stream().filter(split -> split.account().financial())
                .map(TransactionSplit::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The categories of a transaction, or the financial accounts of a transfer. */
    public String label(Transaction transaction) {
        val categories = transaction.splits().stream().map(TransactionSplit::account)
                .filter(account -> !account.financial()).map(Account::name).toList();
        return categories.isEmpty() ? "Transferência" : String.join(", ", categories);
    }

    /** The value of a transaction (see {@link BookTransaction#value}). */
    public BigDecimal value(Transaction transaction) {
        return BookTransaction.value(transaction.splits());
    }

    /**
     * Whether the simple editor fits ({@link #from}, {@link #to}): one account gives, one receives. Any other (an
     * imported paycheck with its deductions) goes to the editor of splits.
     */
    public boolean simple(Transaction transaction) {
        val signs = transaction.splits().stream().map(split -> split.debitAmount().signum()).toList();
        return signs.size() == 2 && signs.contains(1) && signs.contains(-1);
    }

    /** The account the value of a simple transaction leaves: the split that decreases in debit terms. */
    public Account from(Transaction transaction) {
        return transaction.splits().stream().filter(split -> split.debitAmount().signum() < 0)
                .findFirst().orElseThrow().account();
    }

    /** The account the value of a simple transaction goes to. */
    public Account to(Transaction transaction) {
        return transaction.splits().stream().filter(split -> split.debitAmount().signum() > 0)
                .findFirst().orElseThrow().account();
    }

    /** {@code value} from {@code from} to {@code to} (see {@code TransactionService.transfer}). */
    public Result<Transaction, BusinessError> move(LocalDate date, String description, Account from, Account to,
                                                   BigDecimal value) {
        return transactionService.transfer(date, description, from.identity(), to.identity(), value);
    }

    /** Expense: the account pays the category. Income: the category pays the account. */
    public Result<Transaction, BusinessError> quick(LocalDate date, String description, Account account, Account category,
                                                    BigDecimal value) {
        val expense = category.type() == AccountType.EXPENSE;
        return move(date, description, expense ? account : category, expense ? category : account, value);
    }

    /** A complete transaction (INTENT F2). */
    public Result<Transaction, BusinessError> enter(LocalDate date, String description, List<SplitInput> splits) {
        return transactionService.post(date, description, splits);
    }

    /** The transaction rewritten as {@code value} from {@code from} to {@code to}. */
    public Result<Transaction, BusinessError> revise(Transaction transaction, LocalDate date, String description,
                                                     Account from, Account to, BigDecimal value) {
        return rewrite(transaction, date, description, TransactionService.moving(from, to, value));
    }

    public Result<Transaction, BusinessError> rewrite(Transaction transaction, LocalDate date, String description,
                                                      List<SplitInput> splits) {
        return transactionService.revise(transaction.identity(), date, description, splits);
    }

    public Result<Void, BusinessError> discard(Transaction transaction) {
        return transactionService.discard(transaction.identity());
    }

    private static boolean touches(Transaction transaction, Set<Account> accounts) {
        return transaction.splits().stream().anyMatch(split -> accounts.contains(split.account()));
    }

    /** Σ of the splits in {@code accounts}, each converted to the nature {@code debit} (amount × its debit × debit). */
    private static BigDecimal amountIn(Transaction transaction, Set<Account> accounts, int debit) {
        return transaction.splits().stream().filter(split -> accounts.contains(split.account()))
                .map(split -> split.amount().multiply(BigDecimal.valueOf((long) split.account().type().debit * debit)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String counterpart(Transaction transaction, Set<Account> accounts) {
        return transaction.splits().stream().filter(split -> !accounts.contains(split.account()))
                .map(split -> split.account().name()).collect(Collectors.joining(", "));
    }
}
