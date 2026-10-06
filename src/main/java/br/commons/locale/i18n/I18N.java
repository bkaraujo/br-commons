package br.commons.locale.i18n;

import br.commons.locale.l10n.Locales;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The locale of the process and the texts of the application's {@code messages} bundle. The locale comes from the
 * system property {@value #LOCALE_PROPERTY} (language tag, default {@code pt-BR}), reduced to a supported one by
 * {@link Locales#supported}.
 */
@NullMarked
public abstract class I18N {

    public static final String LOCALE_PROPERTY = "br.commons.locale";

    public static Locale locale() {
        return Locales.supported(Locale.forLanguageTag(System.getProperty(LOCALE_PROPERTY, "pt-BR").strip()));
    }

    public static String of(String key, Object... args) {
        return of(key, locale(), args);
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
