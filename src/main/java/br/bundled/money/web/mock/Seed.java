package br.bundled.money.web.mock;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
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
 * A personal book with three months up to {@code today}, on the personal chart of accounts (INTENT F8): salary,
 * rent, bills, card purchases, the card bill paid from the checking account, savings. Variable expenses grow a bit each month so the reports differ.
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

    /** An account added to the template; {@code parent} by name (the template's names are unique). */
    private record Row(String name, AccountType type, String parent) {}

    /** {@code value == null}: the credit card bill, the card balance of the day before. Accounts by name. */
    private record Event(int day, String description, String from, String to, @Nullable BigDecimal value) {}

    /** The financial accounts of the fictitious book, beside the template's {@value #WALLET}. */
    private static final List<Row> MONEY = List.of(
            new Row(CHECKING, AccountType.BANK, "Contas bancárias"),
            new Row(SAVINGS, AccountType.BANK, "Contas bancárias"),
            new Row(CARD, AccountType.CREDIT, "Cartões de crédito"));

    /** The personal chart of accounts (INTENT F8) as the accounts of an empty book, ids from 1 in its order. */
    public static List<MockAccount> template() {
        val entries = ChartTemplate.personal();
        val accounts = new ArrayList<MockAccount>();
        for (val entry : entries) {
            val parent = entry.parent();
            accounts.add(new MockAccount(accounts.size() + 1, entry.name(), entry.type(), parent == null ? null : parent + 1));
        }
        return accounts;
    }

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

    /** The template plus the bank accounts and the credit card. */
    private static List<MockAccount> chart() {
        val accounts = new ArrayList<>(template());
        for (val row : MONEY) {
            accounts.add(new MockAccount(accounts.size() + 1, row.name(), row.type(), id(accounts, row.parent())));
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
                new Event(10, "Internet fibra", CARD, "Internet e telefone", money("119.90")),
                new Event(11, "Hortifruti", CARD, MARKET, money("287.15").multiply(grow)),
                new Event(12, "Pagamento da fatura", CHECKING, CARD, null),
                new Event(14, "Posto", CARD, "Combustível", money("230").multiply(grow)),
                new Event(16, "Corrida", CARD, RIDES, money("27.90")),
                new Event(18, "Supermercado", CARD, MARKET, money("356.80").multiply(grow)),
                new Event(20, "Farmácia", WALLET, "Farmácia", money("64.70").multiply(grow)),
                new Event(21, "Jantar", CARD, RESTAURANTS, money("142").multiply(grow)),
                new Event(22, "Cinema", CARD, "Passeios", money("58")),
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
