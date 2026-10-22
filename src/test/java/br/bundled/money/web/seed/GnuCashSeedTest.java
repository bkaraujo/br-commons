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

    /** 10,00 from the first account ({@code a0}) to the second ({@code a1}). */
    private static final String MOVE = """
            <gnc:transaction version="2.0.0"><trn:id type="guid">t</trn:id>
            <trn:date-posted><ts:date>2026-10-01 10:59:00 +0000</ts:date></trn:date-posted>
            <trn:description>Move</trn:description><trn:splits>
            <trn:split><split:id type="guid">s0</split:id><split:value>-1000/100</split:value>
            <split:account type="guid">a0</split:account></trn:split>
            <trn:split><split:id type="guid">s1</split:id><split:value>1000/100</split:value>
            <split:account type="guid">a1</split:account></trn:split>
            </trn:splits></gnc:transaction>
            """;

    @TempDir
    Path dir;

    /** A book with one top-level account per type ({@code a0}, {@code a1}...) and the transactions given. */
    private Result<Seed.Loaded, BusinessError> read(String transactions, String... types) throws IOException {
        val accounts = new StringBuilder(account("Root", "root", "ROOT", null));
        for (var i = 0; i < types.length; i++) accounts.append(account("Conta " + i, "a" + i, types[i], "root"));
        val file = dir.resolve("book.gnucash");
        Files.writeString(file, """
                <?xml version="1.0" encoding="utf-8" ?>
                <gnc-v2 xmlns:gnc="http://www.gnucash.org/XML/gnc" xmlns:act="http://www.gnucash.org/XML/act"
                        xmlns:trn="http://www.gnucash.org/XML/trn" xmlns:split="http://www.gnucash.org/XML/split"
                        xmlns:ts="http://www.gnucash.org/XML/ts">
                <gnc:book version="2.0.0">%s%s</gnc:book>
                </gnc-v2>
                """.formatted(accounts, transactions));
        return GnuCashSeed.book(file, TODAY);
    }

    private static String account(String name, String guid, String type, @Nullable String parent) {
        return "<gnc:account version=\"2.0.0\"><act:name>" + name + "</act:name><act:id type=\"guid\">" + guid
                + "</act:id><act:type>" + type + "</act:type>"
                + (parent == null ? "" : "<act:parent type=\"guid\">" + parent + "</act:parent>") + "</gnc:account>";
    }

    private static List<AccountType> types(Result<Seed.Loaded, BusinessError> read) {
        return read.getOrThrow().chart().stream().map(ChartTemplate.Entry::type).toList();
    }

    @Test
    void legacyTypesBecomeTheAppsOwn() throws IOException {
        assertEquals(List.of(AccountType.BANK, AccountType.BANK, AccountType.BANK, AccountType.CREDIT, AccountType.EXPENSE),
                types(read("", "CHECKING", "SAVINGS", "MONEYMRKT", "CREDITLINE", "EXPENSE")));
    }

    /** INTENT §4: one currency. An account with no equivalent that moves nothing is left out; the rest loads. */
    @Test
    void accountsWithNoEquivalentAreLeftOutWhileTheyMoveNothing() throws IOException {
        assertEquals(List.of(AccountType.BANK, AccountType.EXPENSE), types(read("", "BANK", "CURRENCY", "EXPENSE")));
        assertEquals(List.of(AccountType.BANK), types(read("", "BANK", "WHATEVER")));
    }

    /** With transactions, leaving it out would change the balances: the load fails as a {@code Result}. */
    @Test
    void accountsWithNoEquivalentThatMoveFail() throws IOException {
        assertEquals(List.of(AccountType.BANK, AccountType.EXPENSE), types(read(MOVE, "BANK", "EXPENSE")));
        assertInstanceOf(Result.Failure.class, read(MOVE, "BANK", "CURRENCY"));
    }

    @Test
    void unreadableFilesFail() throws IOException {
        val file = dir.resolve("broken.gnucash");
        Files.writeString(file, "<gnc-v2><unclosed>");
        assertInstanceOf(Result.Failure.class, GnuCashSeed.book(file, TODAY));
        assertInstanceOf(Result.Failure.class, GnuCashSeed.book(dir.resolve("missing.gnucash"), TODAY));
        assertInstanceOf(Result.Failure.class, read(MOVE.replace("1000/100", "10/0"), "BANK", "EXPENSE"));
    }
}
