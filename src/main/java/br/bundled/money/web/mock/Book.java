package br.bundled.money.web.mock;

import br.bundled.money.business._0_domain.model.AccountType;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The book in memory: the chart of accounts and the transactions (INTENT §6). A ledger account sums its
 * descendants, each split converted to its nature; a financial account shows only its own balance.
 */
@NullMarked
public final class Book {

    private static final String PATH_SEPARATOR = " › ";

    private final List<MockAccount> accounts;
    private final List<MockTransaction> transactions = new CopyOnWriteArrayList<>();
    private final AtomicInteger transactionIds = new AtomicInteger();
    private final AtomicInteger accountIds;
    private final LocalDate today;

    Book(List<MockAccount> accounts, LocalDate today) {
        this.accounts = new CopyOnWriteArrayList<>(accounts);
        this.accountIds = new AtomicInteger(accounts.stream().mapToInt(MockAccount::id).max().orElse(0));
        this.today = today;
    }

    public LocalDate today() {
        return today;
    }

    // ---------- accounts

    public Optional<MockAccount> account(int id) {
        return accounts.stream().filter(account -> account.id() == id).findFirst();
    }

    public List<MockAccount> roots() {
        return accounts.stream().filter(account -> account.parent() == null).toList();
    }

    public List<MockAccount> children(int id) {
        return accounts.stream().filter(account -> Objects.equals(account.parent(), id))
                .sorted(Comparator.comparing(MockAccount::name)).toList();
    }

    /** Every account, depth first, children by name. */
    public List<MockAccount> tree() {
        val all = new ArrayList<MockAccount>();
        roots().forEach(root -> collect(root, all));
        return all;
    }

    private void collect(MockAccount account, List<MockAccount> all) {
        all.add(account);
        children(account.id()).forEach(child -> collect(child, all));
    }

    public int depth(MockAccount account) {
        val parent = account.parent();
        return parent == null ? 0 : 1 + account(parent).map(this::depth).orElse(0);
    }

    /** {@code Despesas › Alimentação › Mercado}. */
    public String path(MockAccount account) {
        val parent = account.parent();
        return parent == null ? account.name()
                : account(parent).map(found -> path(found) + PATH_SEPARATOR + account.name()).orElse(account.name());
    }

    public List<MockAccount> financial() {
        return tree().stream().filter(MockAccount::financial).toList();
    }

    /** Income and expense accounts without children, the ones a quick entry can use. */
    public List<MockAccount> categories() {
        return tree().stream().filter(MockAccount::category).filter(account -> children(account.id()).isEmpty())
                .toList();
    }

    public Optional<MockAccount> root(AccountType type) {
        return roots().stream().filter(account -> account.type() == type).findFirst();
    }

    public Set<Integer> subtree(int id) {
        val ids = new HashSet<Integer>();
        ids.add(id);
        children(id).forEach(child -> ids.addAll(subtree(child.id())));
        return ids;
    }

    /** The accounts a balance or a ledger of {@code id} covers: itself when financial, its subtree otherwise. */
    private Set<Integer> scope(int id) {
        return account(id).filter(MockAccount::financial).isPresent() ? Set.of(id) : subtree(id);
    }

    // ---------- register: create, update, delete (INTENT §6, hierarchy of types)

    /** Whether {@code type} may live under {@code parent}: the same family (asset, liability...). */
    public boolean accepts(AccountType type, MockAccount parent) {
        return family(parent.type()) == family(type);
    }

    private static AccountType family(AccountType type) {
        return ASSETS.contains(type) ? AccountType.ASSET : LIABILITIES.contains(type) ? AccountType.LIABILITY : type;
    }

    private static final Set<AccountType> ASSETS = EnumSet.of(AccountType.BANK, AccountType.CASH, AccountType.STOCK,
            AccountType.MUTUAL, AccountType.ASSET, AccountType.RECEIVABLE);
    private static final Set<AccountType> LIABILITIES = EnumSet.of(AccountType.CREDIT, AccountType.LIABILITY,
            AccountType.PAYABLE);

    /** The parents an account of {@code type} may have; when editing, never itself or a descendant. */
    public List<MockAccount> parents(AccountType type, @Nullable MockAccount editing) {
        val excluded = editing == null ? Set.<Integer>of() : subtree(editing.id());
        return tree().stream().filter(account -> !excluded.contains(account.id()))
                .filter(account -> accepts(type, account)).toList();
    }

    /** How many transactions touch the account itself. */
    public long uses(int id) {
        return transactions.stream().filter(transaction -> touches(transaction, Set.of(id))).count();
    }

    /** Whether the type is fixed: the account has transactions, or subaccounts that must stay in its family. */
    public boolean fixedType(int id) {
        return uses(id) > 0 || !children(id).isEmpty();
    }

    /**
     * Creates the account and, for a financial one with an opening balance, its opening transaction. The opening is
     * in the nature of the account: positive is money in a bank or cash, debt on a credit card.
     */
    public MockAccount create(AccountInput input) {
        val account = new MockAccount(accountIds.incrementAndGet(), input.name(), input.type(), input.parent());
        accounts.add(account);
        val opening = input.opening();
        if (opening != null && opening.signum() != 0) {
            openingAccount().ifPresent(equity -> {
                if (opening.signum() * account.type().debit > 0) move(today, "Saldo inicial", equity.id(), account.id(), opening.abs());
                else move(today, "Saldo inicial", account.id(), equity.id(), opening.abs());
            });
        }
        return account;
    }

    /** Name and parent; the type only while it is not fixed (the caller checks). Empty when the account is gone. */
    public Optional<MockAccount> update(int id, AccountInput input) {
        val updated = new MockAccount(id, input.name(), input.type(), input.parent());
        val replaced = new boolean[1];
        accounts.replaceAll(account -> {
            if (account.id() != id) return account;
            replaced[0] = true;
            return updated;
        });
        return replaced[0] ? Optional.of(updated) : Optional.empty();
    }

    /** Where the transactions of a deleted account may go: any account but the top level and the account itself. */
    public List<MockAccount> targets(int id) {
        return tree().stream().filter(account -> account.parent() != null && account.id() != id).toList();
    }

    /**
     * Moves the splits of the account to {@code target}, converted to its nature (the transaction stays balanced),
     * and removes the account. Without transactions the target is not needed. A transaction that already touched
     * {@code target} has its splits there merged; one left with no effect (a transfer between the two) is removed.
     */
    public void remove(int id, @Nullable Integer target) {
        val from = type(id);
        if (target != null) {
            val factor = BigDecimal.valueOf((long) from.debit * type(target).debit);
            transactions.replaceAll(transaction -> touches(transaction, Set.of(id))
                    ? moved(transaction, id, target, factor) : transaction);
            transactions.removeIf(transaction -> transaction.splits().size() < 2);
        }
        accounts.removeIf(account -> account.id() == id);
    }

    /** The splits of {@code id} moved to {@code target}, the splits per account summed, the zero ones dropped. */
    private static MockTransaction moved(MockTransaction transaction, int id, int target, BigDecimal factor) {
        val merged = new LinkedHashMap<Integer, BigDecimal>();
        transaction.splits().forEach(split -> merged.merge(split.account() == id ? target : split.account(),
                split.account() == id ? split.amount().multiply(factor) : split.amount(), BigDecimal::add));
        return new MockTransaction(transaction.id(), transaction.date(), transaction.description(),
                merged.entrySet().stream().filter(entry -> entry.getValue().signum() != 0)
                        .map(entry -> new MockSplit(entry.getKey(), entry.getValue())).toList());
    }

    private Optional<MockAccount> openingAccount() {
        return root(AccountType.EQUITY).flatMap(equity -> children(equity.id()).stream().findFirst().or(() -> Optional.of(equity)));
    }

    // ---------- balances

    /** Balance at the end of {@code day}, in the nature of the account (see {@link #scope}). */
    public BigDecimal balance(int account, LocalDate day) {
        val ids = scope(account);
        val debit = type(account).debit;
        return transactions.stream().filter(transaction -> !transaction.date().isAfter(day))
                .map(transaction -> amountIn(transaction, ids, debit)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal balance(int account) {
        return balance(account, today);
    }

    /** {@link #balance} of every account at the end of {@code day}, with one pass over the transactions. */
    public Map<Integer, BigDecimal> balances(LocalDate day) {
        val types = new HashMap<Integer, AccountType>();
        val children = new HashMap<Integer, List<Integer>>();
        for (val account : accounts) {
            types.put(account.id(), account.type());
            val parent = account.parent();
            if (parent != null) children.computeIfAbsent(parent, key -> new ArrayList<>()).add(account.id());
        }
        val own = new HashMap<Integer, BigDecimal>();
        for (val transaction : transactions) {
            if (transaction.date().isAfter(day)) continue;
            for (val split : transaction.splits()) {
                val type = types.get(split.account());
                if (type != null) own.merge(split.account(), split.amount().multiply(BigDecimal.valueOf(type.debit)), BigDecimal::add);
            }
        }
        val balances = new HashMap<Integer, BigDecimal>();
        for (val account : accounts) {
            val debit = account.financial() ? own.getOrDefault(account.id(), BigDecimal.ZERO)
                    : subtotal(account.id(), own, children);
            balances.put(account.id(), debit.multiply(BigDecimal.valueOf(account.type().debit)));
        }
        return balances;
    }

    public Map<Integer, BigDecimal> balances() {
        return balances(today);
    }

    /** Σ of the debit terms of {@code id} and its descendants. */
    private static BigDecimal subtotal(int id, Map<Integer, BigDecimal> own, Map<Integer, List<Integer>> children) {
        var total = own.getOrDefault(id, BigDecimal.ZERO);
        for (val child : children.getOrDefault(id, List.of())) total = total.add(subtotal(child, own, children));
        return total;
    }

    /** {@link #balance} converted to the nature of {@code type}: an account of the other nature is negative. */
    public BigDecimal balanceAs(int account, LocalDate day, AccountType type) {
        return balance(account, day).multiply(BigDecimal.valueOf((long) type(account).debit * type.debit));
    }

    /** What moved in the account during the month (see {@link #scope}). */
    public BigDecimal flow(int account, YearMonth month) {
        val ids = scope(account);
        val debit = type(account).debit;
        return transactions.stream().filter(transaction -> YearMonth.from(transaction.date()).equals(month))
                .map(transaction -> amountIn(transaction, ids, debit)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What the categories of {@code type} (income or expense) took in during the month, in its nature, counting only
     * the transactions that touch one of {@code accounts}: a transfer between them adds nothing.
     */
    public BigDecimal categoryFlow(Set<Integer> accounts, YearMonth month, AccountType type) {
        val categories = this.accounts.stream().filter(account -> account.type() == type).map(MockAccount::id)
                .collect(Collectors.toSet());
        return transactions.stream()
                .filter(transaction -> YearMonth.from(transaction.date()).equals(month) && touches(transaction, accounts))
                .map(transaction -> amountIn(transaction, categories, type.debit)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Σ balance × debit of the financial accounts: what is owned minus the credit card debt. */
    public BigDecimal netWorth() {
        return financial().stream()
                .map(account -> balance(account.id()).multiply(BigDecimal.valueOf(account.type().debit)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The month of an account: each transaction with the running balance, starting from the month's opening. */
    public List<LedgerLine> ledger(int account, YearMonth month) {
        val ids = scope(account);
        val debit = type(account).debit;
        var balance = balance(account, month.atDay(1).minusDays(1));
        val lines = new ArrayList<LedgerLine>();
        for (val transaction : sorted()) {
            if (!YearMonth.from(transaction.date()).equals(month) || !touches(transaction, ids)) continue;
            val amount = amountIn(transaction, ids, debit);
            balance = balance.add(amount);
            lines.add(new LedgerLine(transaction, amount, balance, counterpart(transaction, ids)));
        }
        return lines;
    }

    // ---------- transactions

    public List<MockTransaction> recent(int limit) {
        return sorted().reversed().stream().limit(limit).toList();
    }

    /** The effect of a transaction on the net worth: income positive, expense negative, transfers zero. */
    public BigDecimal effect(MockTransaction transaction) {
        return transaction.splits().stream()
                .filter(split -> account(split.account()).map(MockAccount::financial).orElse(false))
                .map(split -> split.amount().multiply(BigDecimal.valueOf(type(split.account()).debit)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The categories of a transaction, or the financial accounts of a transfer. */
    public String label(MockTransaction transaction) {
        val categories = transaction.splits().stream().map(split -> account(split.account()).orElseThrow())
                .filter(account -> !account.financial()).map(MockAccount::name).toList();
        return categories.isEmpty() ? "Transferência" : String.join(", ", categories);
    }

    /** The value of a transaction: the sum of its increasing debit splits. */
    public BigDecimal value(MockTransaction transaction) {
        return transaction.splits().stream()
                .map(split -> split.amount().multiply(BigDecimal.valueOf(type(split.account()).debit)))
                .filter(amount -> amount.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Expense: the account pays the category. Income: the category pays the account. */
    public MockTransaction quick(QuickEntry entry) {
        val expense = type(entry.category()) == AccountType.EXPENSE;
        return move(entry.date(), entry.description(), expense ? entry.account() : entry.category(),
                expense ? entry.category() : entry.account(), entry.amount());
    }

    /**
     * {@code value} goes from {@code from} to {@code to}: debit {@code +value} in {@code to}, {@code -value} in
     * {@code from}, each converted to the sign of its account nature (amount = debit value × type.debit).
     */
    public MockTransaction move(LocalDate date, String description, int from, int to, BigDecimal value) {
        val transaction = new MockTransaction(transactionIds.incrementAndGet(), date, description, splits(from, to, value));
        transactions.add(transaction);
        return transaction;
    }

    /** The two splits of {@code value} going from {@code from} to {@code to} (see {@link #move}). */
    private List<MockSplit> splits(int from, int to, BigDecimal value) {
        val scaled = value.setScale(2, RoundingMode.HALF_UP);
        return List.of(new MockSplit(to, scaled.multiply(BigDecimal.valueOf(type(to).debit))),
                new MockSplit(from, scaled.negate().multiply(BigDecimal.valueOf(type(from).debit))));
    }

    public Optional<MockTransaction> transaction(int id) {
        return transactions.stream().filter(transaction -> transaction.id() == id).findFirst();
    }

    /** The account the value of a two-split transaction leaves: the split that decreases in debit terms. */
    public int from(MockTransaction transaction) {
        return transaction.splits().stream().filter(split -> split.amount().signum() * type(split.account()).debit < 0)
                .findFirst().orElseThrow().account();
    }

    /** The account the value of a two-split transaction goes to. */
    public int to(MockTransaction transaction) {
        return transaction.splits().stream().filter(split -> split.amount().signum() * type(split.account()).debit > 0)
                .findFirst().orElseThrow().account();
    }

    /** The transaction rewritten in place, same id: the splits rebuilt as {@link #move} does. */
    public Optional<MockTransaction> revise(int id, LocalDate date, String description, int from, int to, BigDecimal value) {
        val index = indexOf(id);
        if (index < 0) return Optional.empty();
        val revised = new MockTransaction(id, date, description, splits(from, to, value));
        transactions.set(index, revised);
        return Optional.of(revised);
    }

    public boolean discard(int id) {
        return transactions.removeIf(transaction -> transaction.id() == id);
    }

    private int indexOf(int id) {
        for (var i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).id() == id) return i;
        }
        return -1;
    }

    private List<MockTransaction> sorted() {
        return transactions.stream()
                .sorted(Comparator.comparing(MockTransaction::date).thenComparing(MockTransaction::id)).toList();
    }

    private AccountType type(int account) {
        return account(account).map(MockAccount::type).orElseThrow();
    }

    private static boolean touches(MockTransaction transaction, Set<Integer> ids) {
        return transaction.splits().stream().anyMatch(split -> ids.contains(split.account()));
    }

    /** Σ of the splits in {@code ids}, each converted to the nature {@code debit} (amount × its debit × debit). */
    private BigDecimal amountIn(MockTransaction transaction, Set<Integer> ids, int debit) {
        return transaction.splits().stream().filter(split -> ids.contains(split.account()))
                .map(split -> split.amount().multiply(BigDecimal.valueOf((long) type(split.account()).debit * debit)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String counterpart(MockTransaction transaction, Set<Integer> ids) {
        return transaction.splits().stream().filter(split -> !ids.contains(split.account()))
                .map(split -> account(split.account()).map(MockAccount::name).orElse("?"))
                .collect(Collectors.joining(", "));
    }
}
