package br.commons.serializer.yaml;

import br.commons.logger.LogLevel;
import br.commons.serializer.Yaml;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The overlay gets {@code asEnum} from the default of {@code Serializer}, over its own {@code asString}. */
class OverlayYamlTest {

    record Logging(String level) {}

    @Test
    void enumsFollowTheOverridesAndBindingTheBase() {
        val base = Yaml.from(Map.of("level", "INFO"));
        val overlay = new OverlayYaml(base, key -> "level".equals(key) ? " DEBUG " : null);
        assertEquals(LogLevel.DEBUG, overlay.asEnum("level", LogLevel.class));
        assertEquals(LogLevel.INFO, base.asEnum("level", LogLevel.class));
        assertEquals(new Logging("INFO"), overlay.as(Logging.class));
    }
}
