package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._0_domain.model.Transaction;
import br.bundled.money.business._0_domain.model.TransactionSplit;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The use cases over the in-memory storage, as the app wires them; each test starts from the personal chart. */
@NullMarked
class ApplicationModuleTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 20);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    /** The storage of the test: a restart ({@link #started}) reads back what was written in it. */
    private Storage storage = new InMemoryStorage();

    private void started() {
        Context.clear();
        val kept = storage;
        Context.set(Storage.class, () -> kept);
        Context.set(Clock.class, () -> CLOCK);
        val persistence = new PersistenceModule();
        assertTrue(persistence.initialize().isSuccess());
        assertTrue(new ApplicationModule().initialize().isSuccess());
    }

    private static AccountService accounts() { return Context.get(AccountService.class); }
    private static TransactionService transactions() { return Context.get(TransactionService.class); }
    private static BookService books() { return Context.get(BookService.class); }
    private static Balances balances() { return Context.get(Balances.class); }
    private static ImportService imports() { return Context.get(ImportService.class); }

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
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

    private static Set<UUID> splitsIn(Transaction transaction, Account account) {
        return transaction.splits().stream().filter(split -> split.account().equals(account))
                .map(TransactionSplit::identity).collect(Collectors.toSet());
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
    void anOpeningBalanceGoesToTheOpeningBalancesAccount() {
        accounts().create(new AccountInput("Lucros acumulados", AccountType.EQUITY, named("Patrimônio líquido").identity(),
                null), TODAY).getOrThrow();
        bank("Nubank", "1500");
        assertEquals(new BigDecimal("1500.00"), balance(named("Saldos de abertura"), YearMonth.from(TODAY)),
                "by its name, not the first equity subaccount by name");
        assertEquals(BigDecimal.ZERO, balance(named("Lucros acumulados"), YearMonth.from(TODAY)));
    }

    @Test
    void withoutEquityAnOpeningBalanceIsRefused() {
        imports().load(List.of(new ChartTemplate.Entry("Ativo", AccountType.ASSET, null),
                new ChartTemplate.Entry("Contas bancárias", AccountType.ASSET, 0)), List.of(), null).getOrThrow();
        assertEquals(DomainErrors.ACCOUNT_OPENING_EQUITY, key(accounts().create(new AccountInput("Nubank", AccountType.BANK,
                named("Contas bancárias").identity(), new BigDecimal("100")), TODAY)));
        assertEquals(2, accounts().accounts().size(), "the account is not created without its balance");
    }

    @Test
    void theOpeningEquityIsFoundByNameWhateverTheAccent() {
        imports().load(List.of(new ChartTemplate.Entry("Ativos", AccountType.ASSET, null),
                new ChartTemplate.Entry("Contas", AccountType.ASSET, 0),
                new ChartTemplate.Entry("Resultados", AccountType.EQUITY, null),
                new ChartTemplate.Entry("Ajustes", AccountType.EQUITY, 2),
                new ChartTemplate.Entry("Saldo Inícial", AccountType.EQUITY, 2)), List.of(), null).getOrThrow();
        accounts().create(new AccountInput("Nubank", AccountType.BANK, named("Contas").identity(), new BigDecimal("100")),
                TODAY).getOrThrow();
        assertEquals(new BigDecimal("100.00"), balance(named("Saldo Inícial"), YearMonth.from(TODAY)));
        assertEquals(BigDecimal.ZERO, balance(named("Ajustes"), YearMonth.from(TODAY)));
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

    @Test
    void removingAnAccountKeepsTheOtherSplitsApart() {
        val bank = bank("Nubank", "0");
        val market = named("Mercado");
        val pharmacy = named("Farmácia");
        val health = named("Plano de saúde");
        val posted = transactions().post(TODAY, "Compras", List.of(
                new SplitInput(market.identity(), new BigDecimal("10")),
                new SplitInput(market.identity(), new BigDecimal("20")),
                new SplitInput(pharmacy.identity(), new BigDecimal("15")),
                new SplitInput(bank.identity(), new BigDecimal("-45")))).getOrThrow();
        val inMarket = splitsIn(posted, market);
        val inPharmacy = splitsIn(posted, pharmacy);

        assertTrue(accounts().remove(pharmacy.identity(), health.identity()).isSuccess());
        val moved = transactions().find(posted.identity()).orElseThrow();
        assertEquals(4, moved.splits().size());
        assertEquals(inMarket, splitsIn(moved, market), "the two splits of another account are not merged");
        assertEquals(inPharmacy, splitsIn(moved, health), "the moved split keeps its identity");
        assertEquals(new BigDecimal("15.00"), balance(health, YearMonth.from(TODAY)));
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

    @Test
    void revisingKeepsTheIdentityOfTheSplits() {
        val bank = bank("Nubank", "1000");
        val market = named("Mercado");
        val posted = transactions().transfer(TODAY, "Feira", bank.identity(), market.identity(), new BigDecimal("100")).getOrThrow();
        val before = Set.of(splitsIn(posted, bank), splitsIn(posted, market));

        val revised = transactions().revise(posted.identity(), TODAY, "Feira grande", List.of(
                new SplitInput(market.identity(), new BigDecimal("150")),
                new SplitInput(bank.identity(), new BigDecimal("-150")))).getOrThrow();
        assertEquals(before, Set.of(splitsIn(revised, bank), splitsIn(revised, market)), "the GnuCash GUIDs stay");
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

    @Test
    void anInvalidImportLeavesTheBookAsItWas() {
        val bank = bank("Nubank", "1000");
        val chart = List.of(new ChartTemplate.Entry("Ativo", AccountType.ASSET, null),
                new ChartTemplate.Entry("Carteira", AccountType.CASH, 0));
        val unbalanced = new ImportService.Posting(TODAY, "x",
                List.of(new ImportService.Line(1, BigDecimal.TEN), new ImportService.Line(0, BigDecimal.ONE)));
        assertEquals(DomainErrors.TRANSACTION_UNBALANCED, key(imports().load(chart, List.of(unbalanced), null)));
        val outside = new ImportService.Posting(TODAY, "x",
                List.of(new ImportService.Line(1, BigDecimal.TEN), new ImportService.Line(7, BigDecimal.TEN)));
        assertEquals(DomainErrors.IMPORT_ACCOUNT, key(imports().load(chart, List.of(outside), null)));

        assertEquals(ChartTemplate.personal().size() + 1, accounts().accounts().size());
        assertEquals(new BigDecimal("1000.00"), balance(bank, YearMonth.from(TODAY)));
    }
}
