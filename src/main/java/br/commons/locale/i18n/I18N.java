package br.commons.locale.i18n;

import br.commons.locale.l10n.L10N;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The texts of the application's {@code messages} bundle, in a given locale or in the locale of the process
 * ({@link L10N#locale()}: system property {@value L10N#LOCALE_PROPERTY}, default {@code pt-BR}, reduced to a supported
 * one by {@link L10N#supported}).
 *
 * <p>A module keeps its own bundle by {@link #register(String, String) registering} a key prefix: keys starting with it
 * resolve in that bundle (longest prefix wins); every other key stays in {@code messages}.
 */
@NullMarked
public abstract class I18N {

    private static final String MESSAGES = "messages";
    private static final Map<String, String> BUNDLES = new ConcurrentHashMap<>();

    /** Keys starting with {@code prefix} resolve in {@code baseName} (e.g. {@code "money."} → {@code "br/money/messages"}). */
    public static void register(String prefix, String baseName) {
        BUNDLES.put(prefix, baseName);
    }

    public static String of(String key, Object... args) {
        return of(key, L10N.locale(), args);
    }

    public static String of(String key, Locale locale, Object... args) {
        try {
            val bundle = ResourceBundle.getBundle(baseName(key), locale, I18N.class.getClassLoader());
            val pattern = bundle.getString(key);
            return args.length == 0 ? pattern : String.format(locale, pattern, args);
        } catch (MissingResourceException e) {
            return args.length == 0 ? key : String.format(locale, key, args);
        }
    }

    private static String baseName(String key) {
        var baseName = MESSAGES;
        var length = -1;
        for (val entry : BUNDLES.entrySet()) {
            if (key.startsWith(entry.getKey()) && entry.getKey().length() > length) {
                baseName = entry.getValue();
                length = entry.getKey().length();
            }
        }
        return baseName;
    }
}
