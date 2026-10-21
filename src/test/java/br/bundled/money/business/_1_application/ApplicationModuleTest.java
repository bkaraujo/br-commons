package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.persistence.PersistenceModule;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The use cases over the in-memory storage, as the app wires them; each test starts from the personal chart. */
@NullMarked
class ApplicationModuleTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 20);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private final InMemoryStorage storage = new InMemoryStorage();
    private ApplicationModule app;

    private ApplicationModule started() {
        val persistence = new PersistenceModule(storage);
        assertTrue(persistence.initialize().isSuccess());
        val module = new ApplicationModule(persistence.accounts(), persistence.transactions(), persistence.balances(),
                persistence.books(), CLOCK);
        assertTrue(module.initialize().isSuccess());
        return module;
    }

    @BeforeEach
    void setUp() {
        app = started();
    }

    private Account named(String name) {
        return app.accounts().accounts().stream().filter(account -> account.name().equals(name)).findFirst().orElseThrow();
    }

    private Account bank(String name, String opening) {
        return app.accounts().create(new AccountInput(name, AccountType.BANK, named("Contas bancárias").identity(),
                new BigDecimal(opening)), TODAY).getOrThrow();
    }

    private BigDecimal balance(Account account, YearMonth month) {
        return app.balances().balance(account, month).amount();
    }

    private static String key(Result<?, BusinessError> result) {
        return assertInstanceOf(Result.Failure.class, result).error() instanceof BusinessError error ? error.key() : "";
    }

    // ---------- F8, accounts

    @Test
    void anEmptyBookStartsFromThePersonalChart() {
        assertEquals(ChartTemplate.personal().size(), app.accounts().accounts().size());
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
        assertEquals(DomainErrors.ACCOUNT_FAMILY, key(app.accounts().create(
                new AccountInput("Cartão", AccountType.CREDIT, assets, null), TODAY)));
        assertEquals(DomainErrors.ACCOUNT_TOP_LEVEL, key(app.accounts().create(
                new AccountInput("Outro topo", AccountType.ASSET, null, null), TODAY)));
        assertEquals(DomainErrors.ACCOUNT_NAME, key(app.accounts().create(
                new AccountInput(" ", AccountType.BANK, assets, null), TODAY)));

        val bank = bank("Nubank", "10");
        assertEquals(DomainErrors.ACCOUNT_TYPE_FIXED, key(app.accounts().update(bank.identity(),
                new AccountInput("Nubank", AccountType.CASH, assets, null))));
        assertEquals(DomainErrors.ACCOUNT_PARENT, key(app.accounts().update(named("Contas bancárias").identity(),
                new AccountInput("Contas bancárias", AccountType.ASSET, bank.identity(), null))));
        assertEquals(DomainErrors.ACCOUNT_CHILDREN, key(app.accounts().remove(named("Moradia").identity(), null)));
        assertEquals(DomainErrors.ACCOUNT_TOP_LEVEL, key(app.accounts().remove(named("Despesas").identity(), null)));

        val renamed = app.accounts().update(bank.identity(), new AccountInput("Nu", AccountType.BANK,
                named("Ativo").identity(), null)).getOrThrow();
        assertEquals("Nu", renamed.name());
        assertEquals(named("Ativo"), renamed.parent());
    }

    @Test
    void removingAnAccountMovesItsTransactionsToTheTarget() {
        val nubank = bank("Nubank", "1000");
        val inter = bank("Inter", "0");
        val market = named("Mercado");
        app.transactions().transfer(TODAY, "Feira", nubank.identity(), market.identity(), new BigDecimal("100")).getOrThrow();
        app.transactions().transfer(TODAY, "Reserva", nubank.identity(), inter.identity(), new BigDecimal("50")).getOrThrow();

        assertEquals(DomainErrors.ACCOUNT_TARGET, key(app.accounts().remove(nubank.identity(), null)));
        assertTrue(app.accounts().remove(nubank.identity(), inter.identity()).isSuccess());

        val month = YearMonth.from(TODAY);
        assertEquals(new BigDecimal("900.00"), balance(inter, month), "1000 opening − 100 market; the transfer between the two vanished");
        assertEquals(2, app.transactions().statement(inter, month).size());
        assertEquals(new BigDecimal("100.00"), balance(market, month));
        assertTrue(app.transactions().statement(inter, month).stream().allMatch(Transaction::balanced));
        assertTrue(app.accounts().account(nubank.identity()).isEmpty());
    }

    // ---------- F2, balances

    @Test
    void aTransactionWithManySplits() {
        val bank = bank("Nubank", "0");
        val salary = named("Salário");
        val tax = named("Imposto de renda");
        val health = named("Plano de saúde");
        val posted = app.transactions().post(TODAY, "Salário", List.of(
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
        assertEquals(DomainErrors.TRANSACTION_UNBALANCED, key(app.transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, BigDecimal.TEN), new SplitInput(market, BigDecimal.TEN)))));
        assertEquals(DomainErrors.TRANSACTION_SPLITS, key(app.transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, BigDecimal.TEN), new SplitInput(market, BigDecimal.ZERO)))));
        assertEquals(DomainErrors.AMOUNT_SCALE, key(app.transactions().post(TODAY, "x",
                List.of(new SplitInput(bank, new BigDecimal("1.001")), new SplitInput(market, new BigDecimal("1.001"))))));
        assertEquals(DomainErrors.TRANSACTION_DESCRIPTION, key(app.transactions().transfer(TODAY, " ", bank, market, BigDecimal.TEN)));
        assertEquals(DomainErrors.AMOUNT_POSITIVE, key(app.transactions().transfer(TODAY, "x", bank, market, BigDecimal.ZERO)));
        assertEquals(DomainErrors.ACCOUNT_NOT_FOUND, key(app.transactions().transfer(TODAY, "x", bank,
                java.util.UUID.randomUUID(), BigDecimal.TEN)));
    }

    @Test
    void revisingAcrossMonthsRecalculatesBoth() {
        val bank = bank("Nubank", "1000");
        val market = named("Mercado");
        val october = YearMonth.of(2025, 10);
        val november = YearMonth.from(TODAY);
        val posted = app.transactions().transfer(TODAY, "Feira", bank.identity(), market.identity(), new BigDecimal("100")).getOrThrow();
        assertEquals(new BigDecimal("900.00"), balance(bank, november));

        app.transactions().revise(posted.identity(), october.atDay(5), "Feira", List.of(
                new SplitInput(market.identity(), new BigDecimal("40")),
                new SplitInput(bank.identity(), new BigDecimal("-40")))).getOrThrow();
        assertEquals(new BigDecimal("-40.00"), balance(bank, october));
        assertEquals(new BigDecimal("960.00"), balance(bank, november));
        assertEquals(new BigDecimal("40.00"), balance(market, november));

        assertTrue(app.transactions().discard(posted.identity()).isSuccess());
        assertEquals(new BigDecimal("1000.00"), balance(bank, november));
        assertEquals(DomainErrors.TRANSACTION_NOT_FOUND, key(app.transactions().discard(posted.identity())));
    }

    // ---------- F9

    @Test
    void theClosedPeriodIsImmutable() {
        val bank = bank("Nubank", "1000").identity();
        val market = named("Mercado").identity();
        val old = app.transactions().transfer(LocalDate.of(2025, 9, 10), "Antiga", bank, market, BigDecimal.TEN).getOrThrow();
        app.books().close(LocalDate.of(2025, 10, 1)).getOrThrow();

        assertEquals(DomainErrors.PERIOD_CLOSED, key(app.transactions().transfer(LocalDate.of(2025, 9, 30), "x", bank, market, BigDecimal.ONE)));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(app.transactions().discard(old.identity())));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(app.transactions().revise(old.identity(), TODAY, "x", List.of(
                new SplitInput(market, BigDecimal.ONE), new SplitInput(bank, BigDecimal.ONE.negate())))));
        assertEquals(DomainErrors.PERIOD_CLOSED, key(app.accounts().remove(bank, named("Carteira").identity())));
        assertTrue(app.transactions().transfer(LocalDate.of(2025, 10, 1), "Na data-limite", bank, market, BigDecimal.ONE).isSuccess());

        app.books().close(null).getOrThrow();
        assertTrue(app.transactions().discard(old.identity()).isSuccess());
    }

    // ---------- storage

    @Test
    void everythingSurvivesARestartOverTheSameStorage() {
        val bank = bank("Nubank", "1000");
        app.transactions().transfer(TODAY, "Feira", bank.identity(), named("Mercado").identity(), new BigDecimal("100")).getOrThrow();
        app.books().close(LocalDate.of(2025, 1, 1)).getOrThrow();

        app = started();
        assertEquals(ChartTemplate.personal().size() + 1, app.accounts().accounts().size(), "not started over: the book had accounts");
        assertEquals(new BigDecimal("900.00"), balance(named("Nubank"), YearMonth.from(TODAY)));
        assertEquals(LocalDate.of(2025, 1, 1), app.books().book().threshold());
    }

    @Test
    void startingOverLeavesOnlyTheTemplate() {
        val bank = bank("Nubank", "1000");
        app.books().close(LocalDate.of(2025, 1, 1)).getOrThrow();
        app.books().startEmpty().getOrThrow();

        assertEquals(ChartTemplate.personal().size(), app.accounts().accounts().size());
        assertTrue(app.accounts().account(bank.identity()).isEmpty());
        assertNull(app.books().book().threshold());
        assertEquals(BigDecimal.ZERO, balance(named("Saldos de abertura"), YearMonth.from(TODAY)));
    }
}
