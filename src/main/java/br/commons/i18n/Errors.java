package br.commons.i18n;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Error messages of the whole library, in the locale of {@value #LOCALE_PROPERTY} (system property, language tag,
 * default {@code pt-BR}). Supported: pt-BR and en-US; any other language falls back to en-US. Messages come from
 * {@code br/commons/i18n/errors_<locale>.properties} through the typed {@link ErrorMessages}; HTTP status
 * descriptions from {@code br/commons/i18n/http_status_<locale>.properties}.
 */
@NullMarked
public final class Errors {

    private static final String ERRORS = "br/commons/i18n/errors";
    private static final String HTTP_STATUS = "br/commons/i18n/http_status";
    private static final Map<Locale, ErrorMessages> MESSAGES = new ConcurrentHashMap<>();

    private Errors() {}

    /** Messages in the configured locale; one validated proxy per locale, created on first use. */
    public static ErrorMessages get() {
        return get(I18N.locale());
    }

    public static ErrorMessages get(Locale locale) {
        return MESSAGES.computeIfAbsent(I18N.supported(locale), l -> Bundles.of(ErrorMessages.class, ERRORS, l));
    }


    /** Description of a registered HTTP status code in the configured locale. */
    public static String httpStatus(int code) {
        val bundle = ResourceBundle.getBundle(HTTP_STATUS, I18N.locale(), Errors.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        try {
            return bundle.getString(Integer.toString(code));
        } catch (MissingResourceException e) {
            return Integer.toString(code);
        }
    }

}
