package br.commons.locale.l10n;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

/**
 * The locales the library is localized into: its messages exist in pt-BR (the default) and en-US
 * ({@code br/commons/locale/l10n/*_<locale>.properties}). Any other language falls back to en-US.
 */
@NullMarked
public final class L10N {

    public static final String LOCALE_PROPERTY = "br.commons.locale";

    public static final Locale PT_BR = Locale.of("pt", "BR");
    public static final Locale EN_US = Locale.of("en", "US");

    /** Every locale with a bundle; a new one needs every {@code .properties} of this package in it. */
    public static final List<Locale> SUPPORTED = List.of(PT_BR, EN_US);

    private L10N() {}

    public static Locale locale() {
        return L10N.supported(Locale.forLanguageTag(System.getProperty(LOCALE_PROPERTY, "pt-BR").strip()));
    }

    /** The supported locale for {@code locale}: Portuguese → pt-BR, any other language → en-US. */
    public static Locale supported(Locale locale) {
        return "pt".equals(locale.getLanguage()) ? PT_BR : EN_US;
    }
}
