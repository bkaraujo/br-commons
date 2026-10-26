package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.*;
import br.bundled.money.business._0_domain.repository.AccountRepository;
import br.bundled.money.business._0_domain.repository.BalanceRepository;
import br.bundled.money.business._0_domain.repository.BookRepository;
import br.bundled.money.business._0_domain.repository.TransactionRepository;
import br.commons.cdi.Context;
import br.commons.persistence.Storage;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What is saved through one module is what another module over the same storage loads. */
@NullMarked
class PersistenceModuleTest {

    private final InMemoryStorage storage = new InMemoryStorage();

    @BeforeEach
    void setUp() {
        Context.clear();
        Context.set(Storage.class, () -> storage);
    }

    /** A new module over the same storage, loaded: its repositories replace the ones in the {@link Context}. */
    private static void loaded() {
        assertTrue(new PersistenceModule().initialize().isSuccess());
    }

    private static AccountRepository accounts() { return Context.get(AccountRepository.class); }
    private static TransactionRepository transactions() { return Context.get(TransactionRepository.class); }
    private static BalanceRepository balances() { return Context.get(BalanceRepository.class); }
    private static BookRepository books() { return Context.get(BookRepository.class); }

    private static Account bank(Account parent) {
        val account = new Account();
        account.name("Banco");
        account.type(AccountType.BANK);
        account.parent(parent);
        return account;
    }

    @Test
    void roundTrip() {
        loaded();
        val assets = new Account();
        assets.name("Ativo");
        accounts().save(assets).getOrThrow();
        val bank = bank(assets);
        accounts().save(bank).getOrThrow();
        val expenses = new Account();
        expenses.name("Despesas");
        expenses.type(AccountType.EXPENSE);
        accounts().save(expenses).getOrThrow();
        val transaction = BookTransaction.of(LocalDate.of(2025, 11, 3), "Mercado",
                List.of(BookSplit.of(expenses, new BigDecimal("12.34")), BookSplit.of(bank, new BigDecimal("-12.34"))),
                null, "  Feira do sábado\nsem sacola  ");
        transactions().save(transaction).getOrThrow();
        balances().save(bank, new Balance(YearMonth.of(2025, 11), new BigDecimal("-12.34"))).getOrThrow();
        books().save(new Book(LocalDate.of(2025, 10, 1))).getOrThrow();

        loaded();
        val reloadedBank = accounts().findById(bank.identity()).orElseThrow();
        assertEquals("Banco", reloadedBank.name());
        assertEquals(AccountType.BANK, reloadedBank.type());
        assertEquals(assets, reloadedBank.parent());
        assertEquals(List.of(reloadedBank), accounts().findChildren(assets));

        val reloaded = transactions().findById(transaction.identity()).orElseThrow();
        assertEquals("Mercado", reloaded.description());
        assertEquals("Feira do sábado\nsem sacola", reloaded.note(), "stripped, kept across a reload");
        assertEquals(transaction.date(), reloaded.date());
        assertEquals(transaction.splits().getFirst().identity(), reloaded.splits().getFirst().identity());
        assertEquals(new BigDecimal("12.34"), reloaded.splits().getFirst().amount());
        assertSame(reloadedBank, reloaded.splits().get(1).account(), "splits point to the accounts loaded");
        assertTrue(reloaded.balanced());

        assertEquals(new BigDecimal("-12.34"), balances().findByAccount(bank, YearMonth.of(2026, 2)).orElseThrow().amount());
        assertTrue(balances().findByAccount(bank, YearMonth.of(2025, 10)).isEmpty());
        assertEquals(1, balances().findByAccount(bank, Year.of(2025)).size());
        assertEquals(LocalDate.of(2025, 10, 1), books().find().threshold());
    }

    @Test
    void aTransactionMovedToAnotherMonthLeavesTheOldFile() {
        loaded();
        val expenses = new Account();
        expenses.type(AccountType.EXPENSE);
        val cash = new Account();
        cash.type(AccountType.CASH);
        accounts().save(expenses).getOrThrow();
        accounts().save(cash).getOrThrow();
        val splits = List.of(BookSplit.of(expenses, BigDecimal.TEN), BookSplit.of(cash, BigDecimal.TEN.negate()));
        val transaction = BookTransaction.of(LocalDate.of(2025, 11, 30), "Feira", splits);
        transactions().save(transaction).getOrThrow();
        transactions().save(transaction.revised(LocalDate.of(2025, 12, 1), "Feira", splits)).getOrThrow();

        assertEquals(List.of(StorageTransactionRepository.file(YearMonth.of(2025, 12))),
                storage.listFiles(StorageTransactionRepository.PREFIX, StorageTransactionRepository.SUFFIX));
        loaded();
        assertEquals(1, transactions().findAll().size());

        transactions().delete(transaction).getOrThrow();
        loaded();
        assertTrue(transactions().findAll().isEmpty());
    }

    @Test
    void aSplitOfAnUnknownAccountFailsTheLoad() {
        loaded();
        val known = new Account();
        known.type(AccountType.CASH);
        val unknown = new Account();
        unknown.type(AccountType.EXPENSE);
        accounts().save(known).getOrThrow();
        transactions().save(BookTransaction.of(LocalDate.of(2025, 1, 1), "x",
                List.of(BookSplit.of(unknown, BigDecimal.ONE), BookSplit.of(known, BigDecimal.ONE.negate())))).getOrThrow();

        assertTrue(new PersistenceModule().initialize().isFailure());
    }
}
