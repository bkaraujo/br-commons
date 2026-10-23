package br.bundled.money.web.seed;

import br.bundled.money.business._0_domain.DomainErrors;
import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.bundled.money.business._1_application.ImportService;
import br.commons.Logger;
import br.commons.Result;
import br.commons.business.BusinessError;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * A book read from a GnuCash XML file (gzipped or not), read at startup and never copied into the repository: the
 * chart of accounts and every transaction with its splits. The scheduled transactions' templates are skipped.
 * GnuCash keeps the money accounts as plain {@code ASSET}/{@code LIABILITY}; a leaf among them gets the financial
 * type the app lists in "Minhas contas" by the name of its group: credit card {@code CREDIT}, broker {@code STOCK},
 * bank, benefit or wallet {@code BANK}; anything else (a property, a loan given) keeps its type. Only one currency is
 * expected (the values are taken as they are, in BRL); each transaction is kept balanced to the cent. The read-only
 * threshold of the book becomes the closing date. GnuCash's legacy types become the app's ({@link #TYPES}); an
 * account of a type with no equivalent ({@code CURRENCY}, INTENT §4: one currency) is left out with its subaccounts
 * while they move nothing, and fails the load otherwise. A file that cannot be read or parsed fails too.
 */
@NullMarked
final class GnuCashSeed {

    private static final Pattern CARD_GROUP = Pattern.compile("(?iu)cart(?:[ãa]o|[õo]es)");
    private static final Pattern PURCHASE = Pattern.compile("Evento (\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern BROKER_GROUP = Pattern.compile("(?iu)corretora");
    /**
     * The book option of the read-only threshold, in days before today (0: none), as GnuCash keeps it; it becomes the
     * closing date (INTENT F9).
     */
    private static final String THRESHOLD_KEY = "Day Threshold for Read-Only Transactions (red line)";
    private static final Pattern BANK_GROUP = Pattern.compile("(?iu)conta|banc|benef[ií]cio|carteira");
    /** The app's type of each GnuCash type: the same name, or the legacy ones the app keeps under another name. */
    private static final Map<String, AccountType> TYPES = Stream.concat(
                    Arrays.stream(AccountType.values()).map(type -> Map.entry(type.name(), type)),
                    Stream.of(Map.entry("CHECKING", AccountType.BANK), Map.entry("SAVINGS", AccountType.BANK),
                            Map.entry("MONEYMRKT", AccountType.BANK), Map.entry("CREDITLINE", AccountType.CREDIT)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private GnuCashSeed() {}

    /** An account as GnuCash keeps it: ids are GUIDs, the type its own name. */
    private record Account(String guid, String name, String type, @Nullable String parent) {}

    /** {@code value}: the debit value, GnuCash's sign (positive debits the account). */
    private record Split(String account, BigDecimal value) {}

    private record Transaction(LocalDate date, String description, List<Split> splits, @Nullable LocalDate purchased) {}

    /** What the file holds, before it becomes a chart and postings. */
    private record Read(List<Account> accounts, List<Transaction> transactions, @Nullable LocalDate threshold) {}

    static Result<Seed.Loaded, BusinessError> book(Path file, LocalDate today) {
        return read(file, today).flatMap(read -> book(read.accounts(), read.transactions(), read.threshold()));
    }

    private static Result<Read, BusinessError> read(Path file, LocalDate today) {
        try (val input = open(file)) {
            val reader = factory().createXMLStreamReader(input);
            val accounts = new ArrayList<Account>();
            val transactions = new ArrayList<Transaction>();
            var templates = 0;
            String key = null;
            var thresholdDays = 0L;
            while (reader.hasNext()) {
                val event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    val slot = "slot".equals(reader.getPrefix());
                    switch (reader.getLocalName()) {
                        case "template-transactions" -> templates++;
                        // the book's slots (the accounts' and transactions' are read with them)
                        case "key" -> key = slot ? reader.getElementText() : null;
                        case "value" -> {
                            if (slot && THRESHOLD_KEY.equals(key)) {
                                thresholdDays = Math.round(Double.parseDouble(reader.getElementText().strip()));
                            }
                        }
                        case "account" -> {
                            if (templates == 0 && "gnc".equals(reader.getPrefix())) accounts.add(account(reader));
                        }
                        case "transaction" -> {
                            if (templates == 0 && "gnc".equals(reader.getPrefix())) transactions.add(transaction(reader));
                        }
                        default -> {}
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "template-transactions".equals(reader.getLocalName())) {
                    templates--;
                }
            }
            return Result.success(new Read(accounts, transactions, thresholdDays > 0 ? today.minusDays(thresholdDays) : null));
        } catch (IOException e) {
            return Result.failure(DomainErrors.validation(DomainErrors.IMPORT_UNREADABLE, file, e.toString()));
        } catch (XMLStreamException | NumberFormatException | ArithmeticException | DateTimeException
                 | IndexOutOfBoundsException e) {
            // a number, fraction or date GnuCash would not write is as malformed as a broken XML
            return Result.failure(DomainErrors.validation(DomainErrors.IMPORT_MALFORMED, file, e.toString()));
        }
    }

    private static Result<Seed.Loaded, BusinessError> book(List<Account> read, List<Transaction> transactions,
                                                           @Nullable LocalDate threshold) {
        val byGuid = new HashMap<String, Account>();
        read.forEach(account -> byGuid.put(account.guid(), account));
        val parents = new HashSet<String>();
        read.forEach(account -> { if (account.parent() != null) parents.add(account.parent()); });
        val moving = new HashSet<String>();
        transactions.forEach(transaction -> transaction.splits().forEach(split -> {
            if (split.value().signum() != 0) moving.add(split.account());
        }));

        // Parents before children: positions in the order of a depth-first walk from the top level.
        val positions = new HashMap<String, Integer>();
        val chart = new ArrayList<ChartTemplate.Entry>();
        val children = new LinkedHashMap<@Nullable String, List<Account>>();
        read.stream().filter(account -> !"ROOT".equals(account.type()))
                .forEach(account -> children.computeIfAbsent(topParent(account, byGuid), _ -> new ArrayList<>()).add(account));
        val pending = new ArrayDeque<>(children.getOrDefault(null, List.of()));
        while (!pending.isEmpty()) {
            val account = pending.pop();
            val known = TYPES.get(account.type());
            if (known == null) {
                // its subaccounts go too: they would lose their place in the tree
                if (moves(account, children, moving)) {
                    return Result.failure(DomainErrors.validation(DomainErrors.IMPORT_ACCOUNT_TYPE, account.name(), account.type()));
                }
                Logger.warn(() -> "GnuCash account " + account.name() + " of type " + account.type()
                        + " left out with its subaccounts: no equivalent, no transactions");
                continue;
            }
            val parent = topParent(account, byGuid);
            val type = type(known, parent == null ? null : byGuid.get(parent), parents.contains(account.guid()));
            positions.put(account.guid(), chart.size());
            chart.add(new ChartTemplate.Entry(account.name(), type, parent == null ? null : positions.get(parent)));
            children.getOrDefault(account.guid(), List.of()).reversed().forEach(pending::push);
        }

        val postings = new ArrayList<ImportService.Posting>();
        transactions.sort(Comparator.comparing(Transaction::date));
        for (val transaction : transactions) {
            val kept = transaction.splits().stream()
                    .filter(split -> split.value().signum() != 0 && positions.containsKey(split.account())).toList();
            val values = balanced(kept.stream().map(Split::value).toList());
            val lines = new ArrayList<ImportService.Line>();
            for (var i = 0; i < kept.size(); i++) {
                int position = Objects.requireNonNull(positions.get(kept.get(i).account()));
                lines.add(new ImportService.Line(position, chart.get(position).type().debited(values.get(i))));
            }
            if (lines.size() >= 2) postings.add(new ImportService.Posting(transaction.date(), transaction.description(), lines, transaction.purchased()));
        }
        return Result.success(new Seed.Loaded(chart, postings, threshold));
    }

    /** Whether the account or a subaccount of it has a split with a value. */
    private static boolean moves(Account account, Map<@Nullable String, List<Account>> children, Set<String> moving) {
        if (moving.contains(account.guid())) return true;
        return children.getOrDefault(account.guid(), List.of()).stream().anyMatch(child -> moves(child, children, moving));
    }

    /**
     * The debit values rounded to the cent and summing zero again: what rounding each one alone leaves over (a third of
     * a value, more than two decimals) goes to the biggest one.
     */
    private static List<BigDecimal> balanced(List<BigDecimal> exact) {
        val values = new ArrayList<BigDecimal>();
        exact.forEach(value -> values.add(value.setScale(2, RoundingMode.HALF_UP)));
        val rest = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rest.signum() != 0 && !values.isEmpty()) {
            var biggest = 0;
            for (var i = 1; i < values.size(); i++) {
                if (values.get(i).abs().compareTo(values.get(biggest).abs()) > 0) biggest = i;
            }
            values.set(biggest, values.get(biggest).subtract(rest));
        }
        return values;
    }

    /** The parent, or {@code null} for the top level (a child of GnuCash's root account). */
    private static @Nullable String topParent(Account account, Map<String, Account> byGuid) {
        val parent = account.parent() == null ? null : byGuid.get(account.parent());
        return parent == null || "ROOT".equals(parent.type()) ? null : parent.guid();
    }

    /** {@code type} of a leaf in the money groups narrowed to the financial type of its group (see the class). */
    private static AccountType type(AccountType type, @Nullable Account parent, boolean group) {
        if (group || parent == null) return type;
        return switch (type) {
            case LIABILITY -> CARD_GROUP.matcher(parent.name()).find() ? AccountType.CREDIT : type;
            case ASSET -> BROKER_GROUP.matcher(parent.name()).find() ? AccountType.STOCK
                    : BANK_GROUP.matcher(parent.name()).find() ? AccountType.BANK : type;
            default -> type;
        };
    }

    // ---------- XML

    private static Account account(XMLStreamReader reader) throws XMLStreamException {
        String guid = null, name = null, type = null, parent = null;
        var depth = 1;
        while (depth > 0) {
            val event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) depth--;
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            if (depth++ != 1 || !"act".equals(reader.getPrefix())) continue;
            switch (reader.getLocalName()) {
                case "id" -> { guid = reader.getElementText(); depth--; }
                case "name" -> { name = reader.getElementText(); depth--; }
                case "type" -> { type = reader.getElementText(); depth--; }
                case "parent" -> { parent = reader.getElementText(); depth--; }
                default -> {}
            }
        }
        return new Account(required(guid, "act:id"), required(name, "act:name"), required(type, "act:type"), parent);
    }

    private static Transaction transaction(XMLStreamReader reader) throws XMLStreamException {
        String description = "", date = null, account = null, value = null, slot = null;
        LocalDate purchased = null;
        val splits = new ArrayList<Split>();
        var depth = 1;
        while (depth > 0) {
            val event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if ("split".equals(reader.getLocalName()) && "trn".equals(reader.getPrefix())) {
                    splits.add(new Split(required(account, "split:account"), amount(required(value, "split:value"))));
                    account = null;
                    value = null;
                }
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++;
            val prefix = reader.getPrefix();
            switch (prefix + ":" + reader.getLocalName()) {
                case "trn:description" -> { description = reader.getElementText(); depth--; }
                case "trn:date-posted" -> { date = posted(reader); depth--; }
                case "split:account" -> { account = reader.getElementText(); depth--; }
                case "split:value" -> { value = reader.getElementText(); depth--; }
                case "slot:key" -> { slot = reader.getElementText(); depth--; }
                case "slot:value" -> {
                    if (purchased == null && "notes".equals(slot)) {
                        purchased = purchase(reader.getElementText());
                        depth--;
                    }
                }
                default -> {}
            }
        }
        return new Transaction(LocalDate.parse(required(date, "trn:date-posted").substring(0, 10)), description, splits,
                purchased);
    }

    /** The day of the purchase a note records ({@code Evento 2025-09-08 | ...}); null when it has none. */
    private static @Nullable LocalDate purchase(String notes) {
        val found = PURCHASE.matcher(notes);
        return found.find() ? LocalDate.parse(found.group(1)) : null;
    }

    /**
     * The {@code <ts:date>} of {@code <trn:date-posted>}, to its end. Only this one: date-entered, a split's
     * reconcile-date and the slots have their own {@code <ts:date>}.
     */
    private static String posted(XMLStreamReader reader) throws XMLStreamException {
        String date = null;
        var depth = 1;
        while (depth > 0) {
            val event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("date".equals(reader.getLocalName()) && "ts".equals(reader.getPrefix())) date = reader.getElementText();
                else depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return required(date, "ts:date");
    }

    /** GnuCash's {@code numerator/denominator}, exact (rounded to the cent per transaction, see {@link #balanced}). */
    private static BigDecimal amount(String fraction) {
        val slash = fraction.indexOf('/');
        val numerator = new BigDecimal(slash < 0 ? fraction : fraction.substring(0, slash));
        val denominator = slash < 0 ? BigDecimal.ONE : new BigDecimal(fraction.substring(slash + 1));
        return numerator.divide(denominator, MathContext.DECIMAL128);
    }

    private static <T> T required(@Nullable T value, String element) throws XMLStreamException {
        if (value == null) throw new XMLStreamException("Missing <" + element + ">");
        return value;
    }

    private static InputStream open(Path file) throws IOException {
        val input = new BufferedInputStream(Files.newInputStream(file));
        input.mark(2);
        val gzip = input.read() == 0x1f && input.read() == 0x8b;
        input.reset();
        return gzip ? new GZIPInputStream(input) : input;
    }

    /** No DTD nor external entity: the file is data, nothing in it is fetched. */
    private static XMLInputFactory factory() {
        val factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return factory;
    }
}
