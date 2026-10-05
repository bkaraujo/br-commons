package br.commons.serializer.document.builder;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EntryTest {

    @Test
    void constructor_storesKeyAndValue() {
        val entry = new Entry("myKey", "myValue");
        assertEquals("myKey", entry.key());
        assertEquals("myValue", entry.value());
    }

    @Test
    void toMap_containsKeyValuePair() {
        val entry = new Entry("host", "localhost");
        val map = entry.toMap();
        assertEquals(1, map.size());
        assertEquals("localhost", map.get("host"));
    }

    @Test
    void toMap_numericValue() {
        val entry = new Entry("port", 8080);
        val map = entry.toMap();
        assertEquals(8080, map.get("port"));
    }

    @Test
    void equality_sameFields() {
        val a = new Entry("k", "v");
        val b = new Entry("k", "v");
        assertEquals(a, b);
    }

    @Test
    void equality_differentKey() {
        assertNotEquals(new Entry("k1", "v"), new Entry("k2", "v"));
    }
}
