package br.commons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextNormalizerTest {

    @Test
    void removesAccentsCaseAndRepeatedSpaces() {
        assertEquals("ACAI DA PRACA", TextNormalizer.normalize("  Açaí   da\tPraça "));
        assertEquals("FI 2", TextNormalizer.normalize("ﬁ ²"));
        assertEquals("", TextNormalizer.normalize("   "));
    }
}
