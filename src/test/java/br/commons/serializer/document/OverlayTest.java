package br.commons.serializer.document;

import br.commons.logger.LogLevel;
import br.commons.serializer.Serializer;
import br.commons.serializer.Yaml;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The overlay gets {@code asEnum} from the default of {@code Serializer}, over its own {@code asString}. */
class OverlayTest {

    record Logging(String level) {}

    @Test
    void enumsFollowTheOverridesAndBindingTheBase() {
        val base = Yaml.from(Map.of("level", "INFO"));
        val overlay = Serializer.overlay(base, key -> "level".equals(key) ? " DEBUG " : null);
        assertEquals(LogLevel.DEBUG, overlay.asEnum("level", LogLevel.class));
        assertEquals(LogLevel.INFO, base.asEnum("level", LogLevel.class));
        assertEquals(new Logging("INFO"), overlay.as(Logging.class));
    }

    @Test
    void textListsFollowTheOverrides() {
        val base = Yaml.from(Map.of("ports", "1, 2"));
        val overlay = Serializer.overlay(base, key -> "ports".equals(key) ? "7, 8, 9" : null);
        assertArrayEquals(new int[]{7, 8, 9}, overlay.asInts("ports"));
        assertArrayEquals(new int[]{1, 2}, base.asInts("ports"));
    }
}
