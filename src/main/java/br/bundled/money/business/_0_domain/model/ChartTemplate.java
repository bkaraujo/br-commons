package br.bundled.money.business._0_domain.model;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The personal chart of accounts for Brazil (INTENT F8), applied when an empty book is created (the alternative to
 * importing a GnuCash book). Kept in {@value #RESOURCE}: one account per line, {@code Name | TYPE}, two spaces of
 * indentation per level, {@code #} comments.
 */
@NullMarked
public final class ChartTemplate {

    static final String RESOURCE = "/br/bundled/money/plano-pessoal.txt";

    /** An account of the template; {@code parent}: the position of its parent in the list, parents first. */
    public record Entry(String name, AccountType type, @Nullable Integer parent) {}

    private ChartTemplate() {}

    public static List<Entry> personal() {
        try (val input = Objects.requireNonNull(ChartTemplate.class.getResourceAsStream(RESOURCE), RESOURCE);
             val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return parse(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + RESOURCE, e);
        }
    }

    static List<Entry> parse(List<String> lines) {
        val entries = new ArrayList<Entry>();
        // the open ancestors: path.get(depth) is the position of the last account seen at that depth
        val path = new ArrayList<Integer>();
        for (val line : lines) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            entries.add(entry(line, path));
            path.add(entries.size() - 1);
        }
        return List.copyOf(entries);
    }

    /** The account of a line; {@code path} is left with the ancestors of the next one, up to this depth. */
    private static Entry entry(String line, List<Integer> path) {
        val indent = line.length() - line.stripLeading().length();
        val bar = line.indexOf('|');
        if (indent % 2 != 0 || bar < 0 || indent / 2 > path.size()) {
            throw new IllegalArgumentException("Bad chart template line: " + line);
        }
        val depth = indent / 2;
        val type = AccountType.valueOf(line.substring(bar + 1).strip());
        path.subList(depth, path.size()).clear();
        return new Entry(line.substring(indent, bar).strip(), type, depth == 0 ? null : path.getLast());
    }
}
