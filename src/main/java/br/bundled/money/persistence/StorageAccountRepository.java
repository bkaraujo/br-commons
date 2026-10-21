package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountNature;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** The chart of accounts in {@value #FILE}, one field per account id; the parent by its id. */
@NullMarked
public final class StorageAccountRepository implements AccountRepository {

    static final String FILE = "accounts.json";

    record AccountRow(UUID id, String name, String description, AccountType type, @Nullable UUID parent,
                      long createdAt, long updatedAt) {}

    private static final Comparator<Account> BY_NAME = Comparator.<Account, String>comparing(Account::name).thenComparing(Comparator.naturalOrder());

    private final Storage storage = Context.get(Storage.class);
    private final Map<UUID, Account> accounts = new HashMap<>();

    /** Reads every account, then links each to its parent; a parent that is not there fails the load. */
    public synchronized Result<Void, String> load() {
        accounts.clear();
        val keys = storage.keys(FILE);
        if (keys instanceof Result.Failure<List<String>, String>(var error)) return Result.failure(error);
        val parents = new HashMap<UUID, UUID>();
        for (val key : keys.getOrThrow()) {
            val read = Records.read(storage, FILE, key, AccountRow.class);
            if (read instanceof Result.Failure<AccountRow, String>(var error)) return Result.failure(error);
            val row = read.getOrThrow();
            accounts.put(row.id(), account(row));
            if (row.parent() != null) parents.put(row.id(), row.parent());
        }
        for (val entry : parents.entrySet()) {
            val parent = accounts.get(entry.getValue());
            if (parent == null) return Result.failure(Records.unreadable(entry.getKey().toString(), FILE, "parent " + entry.getValue()));
            Objects.requireNonNull(accounts.get(entry.getKey())).parent(parent);
        }
        return Result.success();
    }

    private static Account account(AccountRow row) {
        val account = new Account();
        account.identity(row.id());
        account.name(row.name());
        account.description(row.description());
        account.type(row.type());
        account.createdAt(row.createdAt());
        account.updatedAt(row.updatedAt());
        return account;
    }

    private static AccountRow row(Account account) {
        val parent = account.parent();
        return new AccountRow(account.identity(), account.name(), account.description(), account.type(),
                parent == null ? null : parent.identity(), account.createdAt(), account.updatedAt());
    }

    @Override
    public synchronized Result<Account, String> save(Account account) {
        return Records.write(storage, FILE, account.identity().toString(), row(account)).map(_ -> {
            accounts.put(account.identity(), account);
            return account;
        });
    }

    @Override
    public synchronized Result<Void, String> delete(Account account) {
        return storage.delete(FILE, account.identity().toString()).ifSuccess(_ -> accounts.remove(account.identity()));
    }

    @Override
    public synchronized List<Account> findAll() {
        return accounts.values().stream().sorted(BY_NAME).toList();
    }

    @Override
    public synchronized Optional<Account> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public synchronized List<Account> findByNature(AccountNature nature) {
        return accounts.values().stream().filter(account -> account.type().nature == nature).sorted(BY_NAME).toList();
    }

    @Override
    public synchronized List<Account> findByType(AccountType type) {
        return accounts.values().stream().filter(account -> account.type() == type).sorted(BY_NAME).toList();
    }

    @Override
    public synchronized List<Account> findChildren(Account parent) {
        return accounts.values().stream().filter(account -> parent.equals(account.parent())).sorted(BY_NAME).toList();
    }
}
