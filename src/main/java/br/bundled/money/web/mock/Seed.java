package br.bundled.money.web.mock;

import br.bundled.money.business._0_domain.model.AccountType;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A personal book with three months up to {@code today}: salary, rent, bills, card purchases, the card bill paid
 * from the checking account, savings. Variable expenses grow a bit each month so the reports differ.
 */
@NullMarked
public final class Seed {

    /** The path of a GnuCash XML book to load instead of the fictitious one. */
    public static final String GNUCASH_PROPERTY = "br.commons.money.seed.gnucash";

    private static final String CHECKING = "Conta corrente";
    private static final String SAVINGS = "Conta Poupança";
    private static final String WALLET = "Carteira";
    private static final String CARD = "Cartão de crédito";
    private static final String OPENING = "Saldos de abertura";
    private static final String MARKET = "Mercado";
    private static final String RESTAURANTS = "Restaurantes";
    private static final String RIDES = "Aplicativos";

    private Seed() {}

    /** An account of the chart; {@code parent} by name (the parents' names are unique). */
    private record Row(String name, AccountType type, @Nullable String parent) {}

    /** {@code value == null}: the credit card bill, the card balance of the day before. Accounts by name. */
    private record Event(int day, String description, String from, String to, @Nullable BigDecimal value) {}

    private static final List<Row> CHART = List.of(
            new Row("Ativo", AccountType.ASSET, null),
            new Row(CHECKING, AccountType.BANK, "Ativo"),
            new Row(SAVINGS, AccountType.BANK, "Ativo"),
            new Row("Conta Investimento", AccountType.BANK, "Ativo"),
            new Row(WALLET, AccountType.CASH, "Ativo"),
            new Row("Passivo", AccountType.LIABILITY, null),
            new Row(CARD, AccountType.CREDIT, "Passivo"),
            new Row("Patrimônio líquido", AccountType.EQUITY, null),
            new Row(OPENING, AccountType.EQUITY, "Patrimônio líquido"),
            new Row("Receitas", AccountType.INCOME, null),
            new Row("CLT", AccountType.INCOME, "Receitas"),
            new Row("Salário", AccountType.INCOME, "CLT"),
            new Row("Benefício", AccountType.INCOME, "CLT"),
            new Row("Restituição", AccountType.INCOME, "CLT"),
            new Row("13º Salário", AccountType.INCOME, "CLT"),
            new Row("CNPJ", AccountType.INCOME, "Receitas"),
            new Row("Pro labore", AccountType.INCOME, "CNPJ"),
            new Row("Restituição", AccountType.INCOME, "CNPJ"),
            new Row("Rendimentos", AccountType.INCOME, "Receitas"),
            new Row("Vendas", AccountType.INCOME, "Receitas"),
            new Row("Despesas", AccountType.EXPENSE, null),
            new Row("Moradia", AccountType.EXPENSE, "Despesas"),
            new Row("Aluguel", AccountType.EXPENSE, "Moradia"),
            new Row("Energia", AccountType.EXPENSE, "Moradia"),
            new Row("Internet", AccountType.EXPENSE, "Moradia"),
            new Row("Alimentação", AccountType.EXPENSE, "Despesas"),
            new Row(MARKET, AccountType.EXPENSE, "Alimentação"),
            new Row(RESTAURANTS, AccountType.EXPENSE, "Alimentação"),
            new Row("Transporte", AccountType.EXPENSE, "Despesas"),
            new Row("Combustível", AccountType.EXPENSE, "Transporte"),
            new Row(RIDES, AccountType.EXPENSE, "Transporte"),
            new Row("Saúde", AccountType.EXPENSE, "Despesas"),
            new Row("Lazer", AccountType.EXPENSE, "Despesas"));

    /**
     * The book: read from the GnuCash file named by {@value #GNUCASH_PROPERTY} when it is set (real data stays out of
     * the repository); else the fictitious one.
     */
    public static Book book(LocalDate today) {
        val gnucash = System.getProperty(GNUCASH_PROPERTY);

        return gnucash == null || gnucash.isBlank() ? fictitious(today) : GnuCashSeed.book(Path.of(gnucash), today);
    }

    private static Book fictitious(LocalDate today) {
        val accounts = chart();
        val book = new Book(accounts, today);
        val first = YearMonth.from(today).minusMonths(2);
        book.move(first.atDay(1), "Saldo inicial", id(accounts, OPENING), id(accounts, CHECKING), money("4800"));
        book.move(first.atDay(1), "Saldo inicial", id(accounts, OPENING), id(accounts, SAVINGS), money("12000"));
        book.move(first.atDay(1), "Saldo inicial", id(accounts, OPENING), id(accounts, WALLET), money("250"));
        val card = id(accounts, CARD);
        for (var i = 0; i < 3; i++) {
            val month = first.plusMonths(i);
            for (val event : events(i)) {
                val date = month.atDay(Math.min(event.day(), month.lengthOfMonth()));
                if (date.isAfter(today)) break;
                val value = event.value() == null ? book.balance(card, date.minusDays(1)) : event.value();
                if (value.signum() > 0) {
                    book.move(date, event.description(), id(accounts, event.from()), id(accounts, event.to()), value);
                }
            }
        }
        return book;
    }

    private static List<MockAccount> chart() {
        val accounts = new ArrayList<MockAccount>();
        for (val row : CHART) {
            accounts.add(new MockAccount(accounts.size() + 1, row.name(), row.type(),
                    row.parent() == null ? null : id(accounts, row.parent())));
        }
        return accounts;
    }

    /** The events of the {@code i}-th month, by day. */
    private static List<Event> events(int i) {
        val grow = BigDecimal.ONE.add(BigDecimal.valueOf(i * 7L, 2));
        val events = new ArrayList<>(List.of(
                new Event(3, "Supermercado", CARD, MARKET, money("412.30").multiply(grow)),
                new Event(4, "Saque", CHECKING, WALLET, money("200")),
                new Event(5, "Salário", "Salário", CHECKING, money("8500")),
                new Event(6, "Reserva mensal", CHECKING, SAVINGS, money("1000")),
                new Event(7, "Aluguel", CHECKING, "Aluguel", money("2200")),
                new Event(8, "Conta de luz", CHECKING, "Energia", money("164.90").multiply(grow)),
                new Event(9, "Almoço de domingo", CARD, RESTAURANTS, money("86.50").multiply(grow)),
                new Event(10, "Internet fibra", CARD, "Internet", money("119.90")),
                new Event(11, "Hortifruti", CARD, MARKET, money("287.15").multiply(grow)),
                new Event(12, "Pagamento da fatura", CHECKING, CARD, null),
                new Event(14, "Posto", CARD, "Combustível", money("230").multiply(grow)),
                new Event(16, "Corrida", CARD, RIDES, money("27.90")),
                new Event(18, "Supermercado", CARD, MARKET, money("356.80").multiply(grow)),
                new Event(20, "Farmácia", WALLET, "Saúde", money("64.70").multiply(grow)),
                new Event(21, "Jantar", CARD, RESTAURANTS, money("142").multiply(grow)),
                new Event(22, "Cinema", CARD, "Lazer", money("58")),
                new Event(25, "Feira", WALLET, MARKET, money("48.60").multiply(grow)),
                new Event(27, "Corrida", CARD, RIDES, money("33.40")),
                new Event(28, "Rendimento da poupança", "Rendimentos", SAVINGS, money("62.35").add(BigDecimal.valueOf(i * 6L)))));
        events.sort(Comparator.comparingInt(Event::day));
        return events;
    }

    /** The first account with this name. */
    private static int id(List<MockAccount> accounts, String name) {
        return accounts.stream().filter(account -> account.name().equals(name)).findFirst().orElseThrow().id();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
