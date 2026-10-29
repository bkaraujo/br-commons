package br.bundled.judas;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every key of a bundle exists in both languages. */
@NullMarked
class BundlesTest {

    /** ARCH-09, UI-02 */
    @ParameterizedTest
    @ValueSource(strings = {"br/bundled/judas/forms", "br/bundled/judas/messages"})
    void bothLanguagesHaveTheSameKeys(String bundle) throws IOException {
        assertEquals(keys(bundle + "_pt_BR.properties"), keys(bundle + "_en_US.properties"));
    }

    private static Set<String> keys(String resource) throws IOException {
        val properties = new Properties();
        val stream = Objects.requireNonNull(BundlesTest.class.getClassLoader().getResourceAsStream(resource), resource);
        try (val reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames();
    }
}
