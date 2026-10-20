package br.commons.locale.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18NTest {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    @Test
    void registeredPrefix_resolvesInItsBundle() {
        I18N.register("apostrophe", "messages/test");
        assertEquals("D'Ávila d'água", I18N.of("apostrophe", PT_BR));
    }

    @Test
    void unknownKey_returnsKeyFormatted() {
        assertEquals("i18n.test.unknown 7", I18N.of("i18n.test.unknown %s", PT_BR, 7));
    }
}
