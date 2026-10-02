package br.commons.i18n;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resolves translation key → text in the requested locale, bundle {@code messages} (root pt-BR,
 * {@code messages_en_US.properties} for English — see {@code br-application/src/main/resources}).
 *
 * <p>Key with no entry in any bundle does not throw: {@code format} runs on the key itself.
 * This is what enables incremental migration of {@link br.commons.business.BusinessError} — a call site
 * not yet migrated to a key continues passing the original pt-BR prose as "key", which never matches
 * any bundle and comes back formatted the same way it always has.
 */
@NullMarked
public abstract class Messages {

    private Messages() {}

    public static String of(String key, Locale locale, Object... args) {
        try {
            val bundle = ResourceBundle.getBundle("messages", locale, Messages.class.getClassLoader());
            val pattern = bundle.getString(key);
            return args.length == 0 ? pattern : String.format(locale, pattern, args);
        } catch (MissingResourceException e) {
            return args.length == 0 ? key : String.format(locale, key, args);
        }
    }
}
