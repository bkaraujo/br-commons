package br.bundled.money.web.seed;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._1_application.ApplicationModule;
import br.bundled.money.business._1_application.ImportService;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.cdi.Context;
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
 * The book a development run starts with. A personal book with three months up to {@code today}, on the personal
 * chart of accounts (INTENT F8): salary, rent, bills, card purchases, the card bill paid from the checking account,
 * savings. Variable expenses grow a bit each month so the reports differ.
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

    /** What a seed loads through {@link ImportService#load}. */
    record Loaded(List<ChartTemplate.Entry> chart, List<ImportService.Posting> postings, @Nullable LocalDate threshold) {}

    /** {@code value == null}: the credit card bill, the card balance of the day before. Accounts by name. */
    private record Event(int day, String description, String from, String to, @Nullable BigDecimal value) {}

    /** An account added to the template; {@code parent} by name (the template's names are unique). */
    private record Row(String name, AccountType type, String parent) {}

    /** The financial accounts of the fictitious book, beside the template's {@value #WALLET}. */
    private static final List<Row> MONEY = List.of(
            new Row(CHECKING, AccountType.BANK, "Contas bancárias"),
            new Row(SAVINGS, AccountType.BANK, "Contas bancárias"),
            new Row(CARD, AccountType.CREDIT, "Cartões de crédito"));

    /**
     * Loads the book of the GnuCash file named by {@value #GNUCASH_PROPERTY} when it is set (real data stays out of
     * the repository); else the fictitious one, only into a book without transactions.
     */
    public static Result<?, BusinessError> load() {
        val app = Context.get(ApplicationModule.class);
        val gnucash = System.getProperty(GNUCASH_PROPERTY);
        val today = app.today();
        if (gnucash != null && !gnucash.isBlank()) {
            val loaded = GnuCashSeed.book(Path.of(gnucash), today);
            return app.imports().load(loaded.chart(), loaded.postings(), loaded.threshold());
        }
        if (!app.transactions().all().isEmpty()) return Result.success();
        val loaded = fictitious(today);
        return app.imports().load(loaded.chart(), loaded.postings(), loaded.threshold());
    }

    private static Loaded fictitious(LocalDate today) {
        val chart = new ArrayList<>(ChartTemplate.personal());
        MONEY.forEach(row -> chart.add(new ChartTemplate.Entry(row.name(), row.type(), position(chart, row.parent()))));
        val postings = new ArrayList<ImportService.Posting>();
        val first = YearMonth.from(today).minusMonths(2);
        postings.add(move(chart, first.atDay(1), "Saldo inicial", OPENING, CHECKING, money("4800")));
        postings.add(move(chart, first.atDay(1), "Saldo inicial", OPENING, SAVINGS, money("12000")));
        postings.add(move(chart, first.atDay(1), "Saldo inicial", OPENING, WALLET, money("250")));
        val card = position(chart, CARD);
        for (var i = 0; i < 3; i++) {
            val month = first.plusMonths(i);
            for (val event : events(i)) {
                val date = month.atDay(Math.min(event.day(), month.lengthOfMonth()));
                if (date.isAfter(today)) break;
                val value = event.value() == null ? balance(postings, card, date.minusDays(1)) : event.value();
                if (value.signum() > 0) postings.add(move(chart, date, event.description(), event.from(), event.to(), value));
            }
        }
        return new Loaded(chart, postings, null);
    }

    /** {@code value} from {@code from} to {@code to}: each line in the nature of its account. */
    private static ImportService.Posting move(List<ChartTemplate.Entry> chart, LocalDate date, String description,
                                              String from, String to, BigDecimal value) {
        val origin = position(chart, from);
        val destination = position(chart, to);
        val scaled = value.setScale(2, RoundingMode.HALF_UP);
        return new ImportService.Posting(date, description, List.of(
                new ImportService.Line(destination, scaled.multiply(BigDecimal.valueOf(chart.get(destination).type().debit))),
                new ImportService.Line(origin, scaled.negate().multiply(BigDecimal.valueOf(chart.get(origin).type().debit)))));
    }

    /** The balance of the account at the end of {@code day}, in its nature, from the postings so far. */
    private static BigDecimal balance(List<ImportService.Posting> postings, int account, LocalDate day) {
        return postings.stream().filter(posting -> !posting.date().isAfter(day))
                .flatMap(posting -> posting.lines().stream()).filter(line -> line.account() == account)
                .map(ImportService.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

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

    /** The position of the first account with this name. */
    private static int position(List<ChartTemplate.Entry> chart, String name) {
        for (var i = 0; i < chart.size(); i++) {
            if (chart.get(i).name().equals(name)) return i;
        }
        throw new IllegalArgumentException(name);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
