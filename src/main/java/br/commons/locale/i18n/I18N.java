package br.commons.locale.i18n;

import lombok.val;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public abstract class I18N {

    public static final String LOCALE_PROPERTY = "br.commons.locale";
    public static final Locale PT_BR = Locale.of("pt", "BR");
    public static final Locale EN_US = Locale.of("en", "US");


    public static Locale locale() {
        return supported(Locale.forLanguageTag(System.getProperty(LOCALE_PROPERTY, "pt-BR").strip()));
    }

    public static Locale supported(Locale locale) {
        return "pt".equals(locale.getLanguage()) ? PT_BR : EN_US;
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
