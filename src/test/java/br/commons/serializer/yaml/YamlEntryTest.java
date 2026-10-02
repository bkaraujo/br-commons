package br.commons.serializer.yaml;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class YamlEntryTest {

    @Test
    void constructor_storesKeyAndValue() {
        val entry = new YamlEntry("myKey", "myValue");
        assertEquals("myKey", entry.key());
        assertEquals("myValue", entry.value());
    }

    @Test
    void toMap_containsKeyValuePair() {
        val entry = new YamlEntry("host", "localhost");
        val map = entry.toMap();
        assertEquals(1, map.size());
        assertEquals("localhost", map.get("host"));
    }

    @Test
    void toMap_numericValue() {
        val entry = new YamlEntry("port", 8080);
        val map = entry.toMap();
        assertEquals(8080, map.get("port"));
    }

    @Test
    void equality_sameFields() {
        val a = new YamlEntry("k", "v");
        val b = new YamlEntry("k", "v");
        assertEquals(a, b);
    }

    @Test
    void equality_differentKey() {
        assertNotEquals(new YamlEntry("k1", "v"), new YamlEntry("k2", "v"));
    }
}
