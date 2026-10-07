package br.commons.locale.i18n;

import br.commons.locale.l10n.L10N;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The texts of the application's {@code messages} bundle, in a given locale or in the locale of the process
 * ({@link L10N#locale()}: system property {@value L10N#LOCALE_PROPERTY}, default {@code pt-BR}, reduced to a supported
 * one by {@link L10N#supported}).
 */
@NullMarked
public abstract class I18N {

    public static String of(String key, Object... args) {
        return of(key, L10N.locale(), args);
    }

    public static String of(String key, Locale locale, Object... args) {
        try {
            val bundle = ResourceBundle.getBundle("messages", locale, I18N.class.getClassLoader());
            val pattern = bundle.getString(key);
            return args.length == 0 ? pattern : String.format(locale, pattern, args);
        } catch (MissingResourceException e) {
            return args.length == 0 ? key : String.format(locale, key, args);
        }
    }
}
