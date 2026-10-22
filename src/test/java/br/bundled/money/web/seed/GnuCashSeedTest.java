package br.bundled.money.web.seed;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._0_domain.model.ChartTemplate;
import br.commons.Result;
import br.commons.business.BusinessError;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GnuCashSeedTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @TempDir
    Path dir;

    private Result<Seed.Loaded, BusinessError> read(String... types) throws IOException {
        val accounts = new StringBuilder(account("Root", "root", "ROOT", null));
        for (var i = 0; i < types.length; i++) accounts.append(account("Conta " + i, "a" + i, types[i], "root"));
        val file = dir.resolve("book.gnucash");
        Files.writeString(file, """
                <?xml version="1.0" encoding="utf-8" ?>
                <gnc-v2 xmlns:gnc="http://www.gnucash.org/XML/gnc" xmlns:act="http://www.gnucash.org/XML/act">
                <gnc:book version="2.0.0">%s</gnc:book>
                </gnc-v2>
                """.formatted(accounts));
        return GnuCashSeed.book(file, TODAY);
    }

    private static String account(String name, String guid, String type, @Nullable String parent) {
        return "<gnc:account version=\"2.0.0\"><act:name>" + name + "</act:name><act:id type=\"guid\">" + guid
                + "</act:id><act:type>" + type + "</act:type>"
                + (parent == null ? "" : "<act:parent type=\"guid\">" + parent + "</act:parent>") + "</gnc:account>";
    }

    @Test
    void legacyTypesBecomeTheAppsOwn() throws IOException {
        val loaded = read("CHECKING", "SAVINGS", "MONEYMRKT", "CREDITLINE", "EXPENSE").getOrThrow();
        assertEquals(List.of(AccountType.BANK, AccountType.BANK, AccountType.BANK, AccountType.CREDIT, AccountType.EXPENSE),
                loaded.chart().stream().map(ChartTemplate.Entry::type).toList());
    }

    /** INTENT §4: one currency; an unknown type fails the start as a {@code Result}, not an exception. */
    @Test
    void currencyAndUnknownTypesFail() throws IOException {
        assertInstanceOf(Result.Failure.class, read("BANK", "CURRENCY"));
        assertInstanceOf(Result.Failure.class, read("WHATEVER"));
    }

    @Test
    void unreadableFilesFail() throws IOException {
        val file = dir.resolve("broken.gnucash");
        Files.writeString(file, "<gnc-v2><unclosed>");
        assertInstanceOf(Result.Failure.class, GnuCashSeed.book(file, TODAY));
        assertInstanceOf(Result.Failure.class, GnuCashSeed.book(dir.resolve("missing.gnucash"), TODAY));
    }
}
