package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.persistence.PersistenceModule;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
import br.commons.persistence.Storage;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The use cases over the in-memory storage, as the app wires them; each test starts from the personal chart. */
@NullMarked
class ApplicationModuleTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 20);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private void started() {
        Context.clear();
        Context.set(Storage.class, InMemoryStorage::new);
        Context.set(Clock.class, () -> CLOCK);
        val persistence = new PersistenceModule();
        assertTrue(persistence.initialize().isSuccess());
        assertTrue(new ApplicationModule().initialize().isSuccess());
    }

    private static AccountService accounts() { return Context.get(AccountService.class); }
    private static TransactionService transactions() { return Context.get(TransactionService.class); }
    private static BookService books() { return Context.get(BookService.class); }
    private static Balances balances() { return Context.get(Balances.class); }

    @BeforeEach
    void setUp() {
        started();
    }

    private Account named(String name) {
        return accounts().accounts().stream().filter(account -> account.name().equals(name)).findFirst().orElseThrow();
    }

    private Account bank(String name, String opening) {
        return accounts().create(new AccountInput(name, AccountType.BANK, named("Contas bancárias").identity(),
                new BigDecimal(opening)), TODAY).getOrThrow();
    }

    private BigDecimal balance(Account account, YearMonth month) {
        return balances().balance(account, month).amount();
    }

    private static String key(Result<?, BusinessError> result) {
        return assertInstanceOf(Result.Failure.class, result).error() instanceof BusinessError error ? error.key() : "";
    }

    // ---------- F8, accounts

    @Test
    void anEmptyBookStartsFromThePersonalChart() {
        assertEquals(ChartTemplate.personal().size(), accounts().accounts().size());
        assertNull(named("Ativo").parent());
        assertEquals(named("Moradia"), named("Aluguel").parent());
    }

    @Test
    void anOpeningBalanceIsPostedAgainstTheOpeningEquity() {
        val bank = bank("Nubank", "1500");
        assertEquals(new BigDecimal("1500.00"), balance(bank, YearMonth.from(TODAY)));
        assertEquals(new BigDecimal("1500.00"), balance(named("Saldos de abertura"), YearMonth.from(TODAY)));
        assertEquals(new BigDecimal("1500.00"), balance(named("Ativo"), YearMonth.from(TODAY)), "the group sums its subtree");
    }

    @Test
    void theHierarchyRules() {
        val assets = named("Contas bancárias").identity();
        assertEquals(DomainErrors.ACCOUNT_FAMILY, key(accounts().create(
                new AccountInput("Cartão", AccountType.CREDIT, assets, null), TODAY)));
        assertEquals(DomainErrors.ACCOUNT_TOP_LEVEL, key(accounts().create(
                new AccountInput("Outro topo", AccountType.ASSET, null, null), TODAY)));
        assertEquals(DomainErrors.ACCOUNT_NAME, key(accounts().create(
                new AccountInput(" ", AccountType.BANK, assets, null), TODAY)));

        val bank = bank("Nubank", "10");
        assertEquals(DomainErrors.ACCOUNT_TYPE_FIXED, key(accounts().update(bank.identity(),
                new AccountInput("Nubank", AccountType.CASH, assets, null))));
        assertEquals(DomainErrors.ACCOUNT_PARENT, key(accounts().update(named("Contas bancárias").identity(),
                new AccountInput("Contas bancárias", AccountType.ASSET, bank.identity(), null))));
        assertEquals(DomainErrors.ACCOUNT_CHILDREN, key(accounts().remove(named("Moradia").identity(), null)));
        assertEquals(DomainErrors.ACCOUNT_TOP_LEVEL, key(accounts().remove(named("Despesas").identity(), null)));

        val renamed = accounts().update(bank.identity(), new AccountInput("Nu", AccountType.BANK,
                named("Ativo").identity(), null)).getOrThrow();
        assertEquals("Nu", renamed.name());
        assertEquals(named("Ativo"), renamed.parent());
    }

    @Test
    void removingAnAccountMovesItsTransactionsToTheTarget() {
        val nubank = bank("Nubank", "1000");
        val inter = bank("Inter", "0");
        val market = named("Mercado");
        transactions().transfer(TODAY, "Feira", nubank.identity(), market.identity(), new BigDecimal("100")).getOrThrow();
        transactions().transfer(TODAY, "Reserva", nubank.identity(), inter.identity(), new BigDecimal("50")).getOrThrow();

        assertEquals(DomainErrors.ACCOUNT_TARGET, key(accounts().remove(nubank.identity(), null)));
        assertTrue(accounts().remove(nubank.identity(), inter.identity()).isSuccess());

        val month = YearMonth.from(TODAY);
        assertEquals(new BigDecimal("900.00"), balance(inter, month), "1000 opening − 100 market; the transfer between the two vanished");
        assertEquals(2, transactions().statement(inter, month).size());
        assertEquals(new BigDecimal("100.00"), balance(market, month));
        assertTrue(transactions().statement(inter, month).stream().allMatch(Transaction::balanced));
        assertTrue(accounts().account(nubank.identity()).isEmpty());
    }

    // ---------- F2, balances

    @Test
    void aTransactionWithManySplits() {
        val bank = bank("Nubank", "0");
        val salary = named("Salário");
        val tax = named("Imposto de renda");
        val health = named("Plano de saúde");
        val posted = transactions().post(TODAY, "Salário", List.of(
                new SplitInput(bank.identity(), new BigDecimal("7000")),
                new SplitInput(tax.identity(), new BigDecimal("1500")),
                new SplitInput(health.identity(), new BigDecimal("500")),
                new SplitInput(salary.identity(), new BigDecimal("9000")))).getOrThrow();

        assertEquals(4, posted.splits().size());
        val month = YearMonth.from(TODAY);
        assertEquals(new BigDecimal("7000.00"), balance(bank, month));
        assertEquals(new BigDecimal("2000.00"), balance(named("Despesas"), month));
        assertEquals(new BigDecimal("9000.00"), balance(named("Receitas"), month));
    }

    @Test
    void theRulesOfATransaction() {
        val bank = bank("Nubank", "0").identity();
        val market = named("Mercado").identity();
        assertEquals(DomainErrors.TRANSACTION_UNBALANCED, key(transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, BigDecimal.TEN), new SplitInput(market, BigDecimal.TEN)))));
        assertEquals(DomainErrors.TRANSACTION_SPLITS, key(transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, BigDecimal.TEN), new SplitInput(market, BigDecimal.ZERO)))));
        assertEquals(DomainErrors.AMOUNT_SCALE, key(transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, new BigDecimal("1.001")), new SplitInput(market, new BigDecimal("1.001"))))));
        assertEquals(DomainErrors.TRANSACTION_DESCRIPTION, key(transactions().transfer(TODAY, " ", bank, market, BigDecimal.TEN)));
        assertEquals(DomainErrors.AMOUNT_POSITIVE, key(transactions().transfer(TODAY, "x", bank, market, BigDecimal.ZERO)));
        assertEquals(DomainErrors.ACCOUNT_NOT_FOUND, key(transactions().transfer(TODAY, "x", bank,
                java.util.UUID.randomUUID(), BigDecimal.TEN)));
    }

    @Test
    void revisingAcrossMonthsRecalculatesBoth() {
        val bank = bank("Nubank", "1000");
        val market = named("Mercado");
        val october = YearMonth.of(2025, 10);
        val november = YearMonth.from(TODAY);
        val posted = transactions().transfer(TODAY, "Feira", bank.identity(), market.identity(), new BigDecimal("100")).getOrThrow();
        assertEquals(new BigDecimal("900.00"), balance(bank, november));

        transactions().revise(posted.identity(), october.atDay(5), "Feira", List.of(
                new SplitInput(market.identity(), new BigDecimal("40")),
                new SplitInput(bank.identity(), new BigDecimal("-40")))).getOrThrow();
        assertEquals(new BigDecimal("-40.00"), balance(bank, october));
        assertEquals(new BigDecimal("960.00"), balance(bank, november));
        assertEquals(new BigDecimal("40.00"), balance(market, november));

        assertTrue(transactions().discard(posted.identity()).isSuccess());
        assertEquals(new BigDecimal("1000.00"), balance(bank, november));
        assertEquals(DomainErrors.TRANSACTION_NOT_FOUND, key(transactions().discard(posted.identity())));
    }

    // ---------- F9

    @Test
    void theClosedPeriodIsImmutable() {
        val bank = bank("Nubank", "1000").identity();
        val market = named("Mercado").identity();
        val old = transactions().transfer(LocalDate.of(2025, 9, 10), "Antiga", bank, market, BigDecimal.TEN).getOrThrow();
        books().close(LocalDate.of(2025, 10, 1)).getOrThrow();

        assertEquals(DomainErrors.PERIOD_CLOSED, key(transactions().transfer(LocalDate.of(2025, 9, 30), "x", bank, market, BigDecimal.ONE)));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(transactions().discard(old.identity())));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(transactions().revise(old.identity(), TODAY, "x", List.of(
                new SplitInput(market, BigDecimal.ONE), new SplitInput(bank, BigDecimal.ONE.negate())))));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(accounts().remove(bank, named("Carteira").identity())));
        assertTrue(transactions().transfer(LocalDate.of(2025, 10, 1), "Na data-limite", bank, market, BigDecimal.ONE).isSuccess());

        books().close(null).getOrThrow();
        assertTrue(transactions().discard(old.identity()).isSuccess());
    }

    // ---------- storage

    @Test
    void everythingSurvivesARestartOverTheSameStorage() {
        val bank = bank("Nubank", "1000");
        transactions().transfer(TODAY, "Feira", bank.identity(), named("Mercado").identity(), new BigDecimal("100")).getOrThrow();
        books().close(LocalDate.of(2025, 1, 1)).getOrThrow();

        started();
        assertEquals(ChartTemplate.personal().size() + 1, accounts().accounts().size(), "not started over: the book had accounts");
        assertEquals(new BigDecimal("900.00"), balance(named("Nubank"), YearMonth.from(TODAY)));
        assertEquals(LocalDate.of(2025, 1, 1), books().book().threshold());
    }

    @Test
    void startingOverLeavesOnlyTheTemplate() {
        val bank = bank("Nubank", "1000");
        books().close(LocalDate.of(2025, 1, 1)).getOrThrow();
        books().startEmpty().getOrThrow();

        assertEquals(ChartTemplate.personal().size(), accounts().accounts().size());
        assertTrue(accounts().account(bank.identity()).isEmpty());
        assertNull(books().book().threshold());
        assertEquals(BigDecimal.ZERO, balance(named("Saldos de abertura"), YearMonth.from(TODAY)));
    }
}
