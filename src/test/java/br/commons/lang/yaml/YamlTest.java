package br.commons.lang.yaml;

import br.commons.lang.Yaml;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlTest {

    // ── factory ───────────────────────────────────────────────────────────────

    @Test
    void from_emptyMap_isEmpty() {
        val yaml = Yaml.from();
        assertTrue(yaml.isEmpty());
    }

    @Test
    void from_populatedMap_notEmpty() {
        val yaml = Yaml.from(Map.of("key", "value"));
        assertFalse(yaml.isEmpty());
    }

    @Test
    void from_nullPath_isEmpty() {
        val yaml = Yaml.from((java.nio.file.Path) null);
        assertTrue(yaml.isEmpty());
    }

    // ── contains ─────────────────────────────────────────────────────────────

    @Test
    void contains_presentKey() {
        val yaml = Yaml.from(Map.of("host", "localhost"));
        assertTrue(yaml.contains("host"));
    }

    @Test
    void contains_missingKey() {
        val yaml = Yaml.from(Map.of("host", "localhost"));
        assertFalse(yaml.contains("port"));
    }

    @Test
    void contains_nestedKey_withDotNotation() {
        val yaml = Yaml.from(Map.of("server", Map.of("port", 8080)));
        assertTrue(yaml.contains("server.port"));
    }

    // ── asString ─────────────────────────────────────────────────────────────

    @Test
    void asString_presentKey() {
        val yaml = Yaml.from(Map.of("name", "Alice"));
        assertEquals("Alice", yaml.asString("name"));
    }

    @Test
    void asString_missingKey_returnsNull() {
        val yaml = Yaml.from(Map.of());
        assertNull(yaml.asString("name"));
    }

    @Test
    void asString_withDefault_missingKey() {
        val yaml = Yaml.from(Map.of());
        assertEquals("default", yaml.asString("name", "default"));
    }

    @Test
    void asString_withDefault_presentKey() {
        val yaml = Yaml.from(Map.of("name", "Alice"));
        assertEquals("Alice", yaml.asString("name", "default"));
    }

    // ── asInt ────────────────────────────────────────────────────────────────

    @Test
    void asInt_integerValue() {
        val yaml = Yaml.from(Map.of("port", 8080));
        assertEquals(8080, yaml.asInt("port"));
    }

    @Test
    void asInt_stringValue_parsed() {
        val yaml = Yaml.from(Map.of("port", "8080"));
        assertEquals(8080, yaml.asInt("port"));
    }

    @Test
    void asInt_missingKey_returnsNull() {
        assertNull(Yaml.from(Map.of()).asInt("port"));
    }

    @Test
    void asInt_withDefault_missingKey() {
        assertEquals(9090, Yaml.from(Map.of()).asInt("port", 9090));
    }

    // ── asLong ───────────────────────────────────────────────────────────────

    @Test
    void asLong_value() {
        val yaml = Yaml.from(Map.of("ttl", 86400L));
        assertEquals(86400L, yaml.asLong("ttl"));
    }

    @Test
    void asLong_withDefault_missingKey() {
        assertEquals(100L, Yaml.from(Map.of()).asLong("ttl", 100L));
    }

    // ── asDouble ─────────────────────────────────────────────────────────────

    @Test
    void asDouble_value() {
        val yaml = Yaml.from(Map.of("ratio", 0.5));
        assertEquals(0.5, yaml.asDouble("ratio"), 0.001);
    }

    @Test
    void asDouble_withDefault_missingKey() {
        assertEquals(1.0, Yaml.from(Map.of()).asDouble("ratio", 1.0), 0.001);
    }

    // ── asBoolean ────────────────────────────────────────────────────────────

    @Test
    void asBoolean_true() {
        val yaml = Yaml.from(Map.of("enabled", true));
        assertTrue(yaml.asBoolean("enabled"));
    }

    @Test
    void asBoolean_false() {
        val yaml = Yaml.from(Map.of("enabled", false));
        assertFalse(yaml.asBoolean("enabled"));
    }

    @Test
    void asBoolean_withDefault_missingKey() {
        assertTrue(Yaml.from(Map.of()).asBoolean("enabled", true));
    }

    // ── asEnum ───────────────────────────────────────────────────────────────

    @Test
    void asEnum_validValue() {
        val yaml = Yaml.from(Map.of("level", "DEBUG"));
        val level = yaml.asEnum("level", br.commons.logger.LogLevel.class);
        assertEquals(br.commons.logger.LogLevel.DEBUG, level);
    }

    @Test
    void asEnum_missingKey_returnsNull() {
        assertNull(Yaml.from(Map.of()).asEnum("level", br.commons.logger.LogLevel.class));
    }

    @Test
    void asEnum_invalidValue_returnsNull() {
        val yaml = Yaml.from(Map.of("level", "NONEXISTENT"));
        assertNull(yaml.asEnum("level", br.commons.logger.LogLevel.class));
    }

    // ── nested / subtree ─────────────────────────────────────────────────────

    @Test
    void subtree_existingKey_returnsSubYaml() {
        val yaml = Yaml.from(Map.of("db", Map.of("host", "localhost", "port", 5432)));
        val sub = yaml.subtree("db");
        assertNotNull(sub);
        assertEquals("localhost", sub.asString("host"));
        assertEquals(5432, sub.asInt("port"));
    }

    @Test
    void subtree_missingKey_returnsNull() {
        assertNull(Yaml.from(Map.of()).subtree("db"));
    }

    @Test
    void dotNotation_nestedValue() {
        val yaml = Yaml.from(Map.of("server", Map.of("host", "example.com")));
        assertEquals("example.com", yaml.asString("server.host"));
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_missingKey_returnsEmpty() {
        assertTrue(Yaml.from(Map.of()).list("items").isEmpty());
    }

    // ── folder ────────────────────────────────────────────────────────────────

    @Test
    void folder_fromMap_returnsNull() {
        assertNull(Yaml.from(Map.of()).folder());
    }

    // ── binding and policy kept by the reader ────────────────────────────────

    record Server(String host, int port) {}

    @Test
    void as_bindsTheDocumentToARecord() {
        assertEquals(new Server("localhost", 8080), Yaml.from(Map.of("host", "localhost", "port", 8080)).as(Server.class));
    }

    @Test
    void asString_trimsAndStillReadsNestedKeys() {
        val yaml = Yaml.from(Map.of("server", Map.of("name", "  Alice  ")));
        assertEquals("Alice", yaml.asString("server.name"));
        assertTrue(yaml.contains("server.name"));
    }
}
