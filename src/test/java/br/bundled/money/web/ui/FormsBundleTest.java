package br.bundled.money.web.ui;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.web.views.Screen;
import br.bundled.money.web.views.Theme;
import br.bundled.money.web.views.Ui;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interface texts of the web ({@link Ui#BUNDLE}): pt_BR and en_US have the same keys, every key the code names
 * is in them, and no screen shows a text that is not a key.
 */
class FormsBundleTest {

    private static final Path WEB = Path.of("src/main/java/br/bundled/money/web");
    private static final Pattern KEY = Pattern.compile("\"(" + Pattern.quote(Ui.PREFIX) + "[A-Za-z0-9_.]*[A-Za-z0-9_])\"");
    private static final Pattern NOT_A_KEY = Pattern.compile("\\bmessage\\(\"(?!" + Pattern.quote(Ui.PREFIX) + ")");

    private static Set<String> keys(Locale locale) {
        val control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        return ResourceBundle.getBundle(Ui.BUNDLE, locale, FormsBundleTest.class.getClassLoader(), control).keySet();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(WEB)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void bothLocalesHaveTheSameKeys() {
        val portuguese = new TreeSet<>(keys(Locale.of("pt", "BR")));
        val english = new TreeSet<>(keys(Locale.of("en", "US")));
        assertEquals(portuguese, english);
    }

    @Test
    void everyKeyTheCodeNamesExists() throws IOException {
        val known = keys(Locale.of("pt", "BR"));
        val missing = new TreeSet<String>();
        for (val file : sources()) {
            val matcher = KEY.matcher(Files.readString(file));
            while (matcher.find()) {
                if (!known.contains(matcher.group(1))) missing.add(file.getFileName() + ": " + matcher.group(1));
            }
        }
        for (val screen : Screen.values()) {
            if (!known.contains(Ui.PREFIX + "screen." + screen.name())) missing.add("Screen." + screen.name());
        }
        for (val theme : Theme.values()) {
            if (!known.contains(Ui.PREFIX + "theme." + theme.name())) missing.add("Theme." + theme.name());
        }
        for (val type : AccountType.values()) {
            if (!known.contains(Ui.PREFIX + "account_type." + type.name())) missing.add("AccountType." + type.name());
        }
        assertTrue(missing.isEmpty(), "missing keys: " + missing);
    }

    @Test
    void noScreenShowsALiteralText() throws IOException {
        val literal = new ArrayList<String>();
        for (val file : sources()) {
            val lines = Files.readAllLines(file);
            for (var i = 0; i < lines.size(); i++) {
                if (NOT_A_KEY.matcher(lines.get(i)).find()) literal.add(file.getFileName() + ":" + (i + 1));
            }
        }
        assertTrue(literal.isEmpty(), "message(\"...\") with a text instead of a key: " + literal);
    }
}
