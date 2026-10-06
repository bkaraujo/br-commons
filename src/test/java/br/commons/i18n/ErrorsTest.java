package br.commons.i18n;

import br.commons.http.HTTPStatus;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Toda mensagem de erro da biblioteca existe em pt-BR e en-US; o locale vem de {@code br.commons.locale}. */
class ErrorsTest {

    @AfterEach
    void reset() {
        System.clearProperty(I18N.LOCALE_PROPERTY);
    }

    @Test
    void errorBundleIsCompleteInEverySupportedLocale() {
        for (val locale : new Locale[] {I18N.PT_BR, I18N.EN_US}) {
            assertEquals(java.util.List.of(), Bundles.check(ErrorMessages.class, "br/commons/i18n/errors", locale), locale.toString());
        }
    }

    @Test
    void everyRegisteredHttpStatusHasADescriptionInEverySupportedLocale() throws IllegalAccessException {
        for (val locale : new Locale[] {I18N.PT_BR, I18N.EN_US}) {
            val bundle = ResourceBundle.getBundle("br/commons/i18n/http_status", locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            for (val field : HTTPStatus.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != HTTPStatus.class) continue;
                val code = Integer.toString(((HTTPStatus) field.get(null)).code());
                assertTrue(bundle.containsKey(code), locale + " missing " + code);
            }
            assertEquals(HTTPStatus.class.getFields().length, bundle.keySet().size(), locale + " orphan keys");
        }
    }

    @Test
    void defaultLocaleIsPortuguese() {
        assertEquals("Rota não encontrada", Errors.get().routeNotFound().value());
        assertEquals("Recurso não encontrado", HTTPStatus.NOT_FOUND.description());
    }

    @Test
    void propertySwitchesToEnglish() {
        System.setProperty(I18N.LOCALE_PROPERTY, "en-US");
        assertEquals("Route not found", Errors.get().routeNotFound().value());
        assertEquals("Resource not found", HTTPStatus.NOT_FOUND.description());
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        System.setProperty(I18N.LOCALE_PROPERTY, "fr-FR");
        assertEquals(I18N.EN_US, I18N.locale());
    }

    @Test
    void numbersAsTextGetNoThousandsSeparator() {
        assertEquals("Falha ao iniciar servidor HTTP na porta 8080: x",
                Errors.get().httpServerStartFailed("8080", "x").value());
    }
}
