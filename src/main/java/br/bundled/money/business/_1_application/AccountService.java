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
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

/**
 * The chart of accounts (INTENT §6): the top level is fixed; a subaccount is of its parent's family; the type changes
 * only without transactions and subaccounts; an account with subaccounts is not removed, one with transactions moves
 * them to another account first (each split converted to its nature, the transaction stays balanced).
 */
@NullMarked
public final class AccountService {

    /** The description of the opening transaction of an account created with a balance. */
    static final String OPENING = "Saldo inicial";

    /**
     * The names of the opening equity, without accents and in lower case: the personal chart's ("Saldos de abertura"),
     * the usual Portuguese ones ("Saldo inicial") and GnuCash's in English ("Opening Balances").
     */
    private static final Pattern OPENING_EQUITY = Pattern.compile("saldos? (de abertura|inicial|iniciais)|opening balances?");

    private final AccountRepository accounts = Context.get(AccountRepository.class);
    private final TransactionRepository transactions = Context.get(TransactionRepository.class);
    private final BalanceRepository kept = Context.get(BalanceRepository.class);
    private final BookRepository books = Context.get(BookRepository.class);
    private final Balances balances = Context.get(Balances.class);
    private final TransactionService posting = Context.get(TransactionService.class);
    private final Lock lock = Context.get(BookLock.class);

    AccountService() {}

    public List<Account> accounts() {
        return accounts.findAll();
    }

    public Optional<Account> account(UUID id) {
        return accounts.findById(id);
    }

    public List<Account> children(Account parent) {
        return accounts.findChildren(parent);
    }

    /** How many transactions have a split in the account itself. */
    public int uses(Account account) {
        return transactions.findByAccount(account).size();
    }

    /**
     * A new account under {@code input.parent()}; with an opening balance, also its opening transaction dated
     * {@code today} against the opening equity ({@link #openingEquity}); without one, the balance is refused. When the
     * opening transaction cannot be stored, the account is undone: it does not stay without its balance.
     */
    public Result<Account, BusinessError> create(AccountInput input, LocalDate today) {
        return Rules.locked(lock, () -> {
            val opening = Objects.requireNonNullElse(input.opening(), BigDecimal.ZERO);
            val with = opening.signum() != 0;
            if (with && opening.stripTrailingZeros().scale() > 2) {
                return Result.failure(DomainErrors.validation(DomainErrors.AMOUNT_SCALE, opening));
            }
            if (with) books.find().open(today, today);
            val parent = parent(input);
            if (parent instanceof Result.Failure<Account, BusinessError>(var error)) return Result.failure(error);
            val equity = with ? openingEquity() : Optional.<Account>empty();
            if (with && equity.isEmpty()) {
                return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_OPENING_EQUITY, input.name().strip()));
            }
            val account = new Account();
            account.name(input.name().strip());
            account.type(input.type());
            account.parent(parent.getOrThrow());
            val saved = Rules.stored(accounts.save(account));
            if (saved.isFailure() || equity.isEmpty()) return saved;
            // o saldo na natureza da conta; a contrapartida fecha a soma em débito
            val amount = opening.setScale(2, RoundingMode.UNNECESSARY);
            val counter = amount.negate().multiply(BigDecimal.valueOf((long) input.type().debit * equity.get().type().debit));
            val posted = posting.post(today, OPENING, List.of(new SplitInput(account.identity(), amount),
                    new SplitInput(equity.get().identity(), counter)));
            if (posted.isFailure()) undo(account);
            return posted.map(_ -> account);
        });
    }

    /** What was written of a new account whose opening failed, removed (best effort: the storage just failed). */
    private void undo(Account account) {
        transactions.findByAccount(account).forEach(transactions::delete);
        kept.delete(account).flatMap(_ -> accounts.delete(account));
    }

    /** Name, parent and (while not fixed) type; the opening balance is not changed here. */
    public Result<Account, BusinessError> update(UUID id, AccountInput input) {
        return Rules.locked(lock, () -> {
            val found = accounts.findById(id);
            if (found.isEmpty()) return Result.failure(DomainErrors.notFound(DomainErrors.ACCOUNT_NOT_FOUND, id));
            val account = found.get();
            if (account.parent() == null) return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_TOP_LEVEL, account.name()));
            if (input.type() != account.type() && (uses(account) > 0 || !accounts.findChildren(account).isEmpty())) {
                return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_TYPE_FIXED, account.name()));
            }
            val parent = parent(input);
            if (parent instanceof Result.Failure<Account, BusinessError>(var error)) return Result.failure(error);
            if (balances.subtree(account).contains(parent.getOrThrow())) {
                return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_PARENT, account.name(), parent.getOrThrow().name()));
            }
            // a conta é a mesma instância que os splits referenciam: muda no lugar e volta se a gravação falhar
            record Before(String name, AccountType type, @Nullable Account parent, long updatedAt) {}
            val before = new Before(account.name(), account.type(), account.parent(), account.updatedAt());
            account.name(input.name().strip());
            account.type(input.type());
            account.parent(parent.getOrThrow());
            account.updatedAt(System.currentTimeMillis());
            val saved = Rules.stored(accounts.save(account));
            if (saved.isFailure()) {
                account.name(before.name());
                account.type(before.type());
                account.parent(before.parent());
                account.updatedAt(before.updatedAt());
            }
            return saved;
        });
    }

    /**
     * Removes the account; its transactions first move to {@code target} (required when there are any): the splits
     * converted to the target's nature and merged with the target's own; a transaction left with fewer than two
     * splits (a transfer between the two) is removed.
     */
    public Result<Void, BusinessError> remove(UUID id, @Nullable UUID target) {
        return Rules.locked(lock, () -> {
            val found = accounts.findById(id);
            if (found.isEmpty()) return Result.failure(DomainErrors.notFound(DomainErrors.ACCOUNT_NOT_FOUND, id));
            val account = found.get();
            if (account.parent() == null) return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_TOP_LEVEL, account.name()));
            if (!accounts.findChildren(account).isEmpty()) {
                return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_CHILDREN, account.name()));
            }
            val moving = transactions.findByAccount(account);
            moving.forEach(transaction -> books.find().open(transaction.date(), account.name()));
            if (!moving.isEmpty()) {
                val destination = target == null ? Optional.<Account>empty() : accounts.findById(target);
                if (destination.isEmpty() || destination.get().equals(account) || destination.get().parent() == null) {
                    return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_TARGET, account.name()));
                }
                val moved = move(account, destination.get(), moving);
                if (moved.isFailure()) return moved;
            }
            return Rules.stored(kept.delete(account).flatMap(_ -> accounts.delete(account)));
        });
    }

    private Result<Void, BusinessError> move(Account account, Account target, List<Transaction> moving) {
        val factor = BigDecimal.valueOf((long) account.type().debit * target.type().debit);
        val touched = new LinkedHashSet<Account>();
        var from = YearMonth.from(moving.getFirst().date());
        for (val transaction : moving) {
            transaction.splits().forEach(split -> touched.add(split.account()));
            from = YearMonth.from(transaction.date()).isBefore(from) ? YearMonth.from(transaction.date()) : from;
            val splits = merged(transaction, account, target, factor);
            val written = splits.size() < 2 ? transactions.delete(transaction)
                    : transactions.save(Rules.revised(transaction, transaction.date(), transaction.description(), splits)).flatMap(_ -> Result.success());
            if (written.isFailure()) return Rules.stored(written);
        }
        touched.remove(account);
        touched.add(target);
        return Rules.stored(balances.refresh(touched, from));
    }

    /**
     * The splits with the account's moved to the target: converted and added to the target's first split (or, when
     * the target is not in the transaction, to the account's first, which keeps its identity); a zero sum is dropped.
     * Every other split stays as it was.
     */
    private static List<BookSplit> merged(Transaction transaction, Account account, Account target, BigDecimal factor) {
        val moved = transaction.splits().stream().filter(split -> split.account().equals(account))
                .map(split -> split.amount().multiply(factor)).reduce(BigDecimal.ZERO, BigDecimal::add);
        // o split que já estava na conta de destino guarda a identidade
        val keeper = transaction.splits().stream().filter(split -> split.account().equals(target)).findFirst()
                .or(() -> transaction.splits().stream().filter(split -> split.account().equals(account)).findFirst())
                .orElseThrow();
        val splits = new ArrayList<BookSplit>();
        for (val split : transaction.splits()) {
            if (split.identity().equals(keeper.identity())) {
                val amount = split.account().equals(target) ? split.amount().add(moved) : moved;
                if (amount.signum() != 0) {
                    splits.add(new BookSplit(split.identity(), target, amount, split.createdAt(), System.currentTimeMillis()));
                }
            } else if (!split.account().equals(account)) {
                splits.add(new BookSplit(split.identity(), split.account(), split.amount(), split.createdAt(), split.updatedAt()));
            }
        }
        return splits;
    }

    /** The parent asked: it exists and is of the account's family. */
    private Result<Account, BusinessError> parent(AccountInput input) {
        if (input.name().isBlank()) return Result.failure(DomainErrors.validation(DomainErrors.ACCOUNT_NAME));
        val id = input.parent();
        if (id == null) return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_TOP_LEVEL, input.name()));
        val parent = accounts.findById(id);
        if (parent.isEmpty()) return Result.failure(DomainErrors.notFound(DomainErrors.ACCOUNT_NOT_FOUND, id));
        if (parent.get().type().family() != input.type().family()) {
            return Result.failure(DomainErrors.rule(DomainErrors.ACCOUNT_FAMILY, input.type(), parent.get().name()));
        }
        return Result.success(parent.get());
    }

    /**
     * The equity account named as the opening balances ({@link #OPENING_EQUITY}); else the only subaccount of the
     * top-level equity; else the top level itself.
     */
    private Optional<Account> openingEquity() {
        val equity = accounts.findByType(AccountType.EQUITY);
        return equity.stream().filter(account -> OPENING_EQUITY.matcher(plain(account.name())).matches()).findFirst()
                .or(() -> equity.stream().filter(account -> account.parent() == null).findFirst().map(root -> {
                    val children = accounts.findChildren(root);
                    return children.size() == 1 ? children.getFirst() : root;
                }));
    }

    /** {@code Saldo Inícial} → {@code saldo inicial}. */
    private static String plain(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").strip().toLowerCase(Locale.ROOT);
    }
}
