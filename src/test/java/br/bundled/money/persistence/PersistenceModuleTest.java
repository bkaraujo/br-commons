package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.model.*;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
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

    private PersistenceModule loaded() {
        val module = new PersistenceModule(storage);
        assertTrue(module.initialize().isSuccess());
        return module;
    }

    private static Account bank(Account parent) {
        val account = new Account();
        account.name("Banco");
        account.type(AccountType.BANK);
        account.parent(parent);
        return account;
    }

    @Test
    void roundTrip() {
        val first = loaded();
        val assets = new Account();
        assets.name("Ativo");
        first.accounts().save(assets).getOrThrow();
        val bank = bank(assets);
        first.accounts().save(bank).getOrThrow();
        val expenses = new Account();
        expenses.name("Despesas");
        expenses.type(AccountType.EXPENSE);
        first.accounts().save(expenses).getOrThrow();
        val transaction = BookTransaction.of(LocalDate.of(2025, 11, 3), "Mercado",
                List.of(BookSplit.of(expenses, new BigDecimal("12.34")), BookSplit.of(bank, new BigDecimal("-12.34"))));
        first.transactions().save(transaction).getOrThrow();
        first.balances().save(bank, new Balance(YearMonth.of(2025, 11), new BigDecimal("-12.34"))).getOrThrow();
        first.books().save(new Book(LocalDate.of(2025, 10, 1))).getOrThrow();

        val second = loaded();
        val reloadedBank = second.accounts().findById(bank.identity()).orElseThrow();
        assertEquals("Banco", reloadedBank.name());
        assertEquals(AccountType.BANK, reloadedBank.type());
        assertEquals(assets, reloadedBank.parent());
        assertEquals(List.of(reloadedBank), second.accounts().findChildren(assets));

        val reloaded = second.transactions().findById(transaction.identity()).orElseThrow();
        assertEquals("Mercado", reloaded.description());
        assertEquals(transaction.date(), reloaded.date());
        assertEquals(transaction.splits().getFirst().identity(), reloaded.splits().getFirst().identity());
        assertEquals(new BigDecimal("12.34"), reloaded.splits().getFirst().amount());
        assertSame(reloadedBank, reloaded.splits().get(1).account(), "splits point to the accounts loaded");
        assertTrue(reloaded.balanced());

        assertEquals(new BigDecimal("-12.34"), second.balances().findByAccount(bank, YearMonth.of(2026, 2)).orElseThrow().amount());
        assertTrue(second.balances().findByAccount(bank, YearMonth.of(2025, 10)).isEmpty());
        assertEquals(1, second.balances().findByAccount(bank, Year.of(2025)).size());
        assertEquals(LocalDate.of(2025, 10, 1), second.books().find().threshold());
    }

    @Test
    void aTransactionMovedToAnotherMonthLeavesTheOldFile() {
        val module = loaded();
        val expenses = new Account();
        expenses.type(AccountType.EXPENSE);
        val cash = new Account();
        cash.type(AccountType.CASH);
        module.accounts().save(expenses).getOrThrow();
        module.accounts().save(cash).getOrThrow();
        val splits = List.of(BookSplit.of(expenses, BigDecimal.TEN), BookSplit.of(cash, BigDecimal.TEN.negate()));
        val transaction = BookTransaction.of(LocalDate.of(2025, 11, 30), "Feira", splits);
        module.transactions().save(transaction).getOrThrow();
        module.transactions().save(transaction.revised(LocalDate.of(2025, 12, 1), "Feira", splits)).getOrThrow();

        assertEquals(List.of(StorageTransactionRepository.file(YearMonth.of(2025, 12))),
                storage.listFiles(StorageTransactionRepository.PREFIX, StorageTransactionRepository.SUFFIX));
        assertEquals(1, loaded().transactions().findAll().size());

        module.transactions().delete(transaction).getOrThrow();
        assertTrue(loaded().transactions().findAll().isEmpty());
    }

    @Test
    void aSplitOfAnUnknownAccountFailsTheLoad() {
        val module = loaded();
        val known = new Account();
        known.type(AccountType.CASH);
        val unknown = new Account();
        unknown.type(AccountType.EXPENSE);
        module.accounts().save(known).getOrThrow();
        module.transactions().save(BookTransaction.of(LocalDate.of(2025, 1, 1), "x",
                List.of(BookSplit.of(unknown, BigDecimal.ONE), BookSplit.of(known, BigDecimal.ONE.negate())))).getOrThrow();

        assertTrue(new PersistenceModule(storage).initialize().isFailure());
    }
}
