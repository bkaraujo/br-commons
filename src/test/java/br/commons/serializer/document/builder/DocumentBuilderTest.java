package br.commons.serializer.document.builder;

import br.commons.serializer.Serializer;
import br.commons.lang.Yaml;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentBuilderTest {

    @Test
    void putTopLevelAndNestedKeys() {
        val doc = Serializer.builder().put("name", "Bob").put("server.port", "9090").build();
        assertEquals("Bob", doc.asString("name"));
        assertEquals(9090, doc.asInt("server.port"));
    }

    @Test
    void appendKeyValueAndEntries() {
        val doc = Serializer.builder()
                .append("env", "production")
                .append(new Entry("host", "localhost"))
                .append("users", new Entry("name", "ana"), new Entry("name", "bia"))
                .append("users", "name", "caio")
                .build();
        assertEquals("production", doc.asString("env"));
        assertEquals("localhost", doc.asString("host"));
        assertEquals(3, doc.list("users").size());
        assertEquals("caio", doc.asString("users.2.name"));
    }

    @Test
    void clearRemovesAllData() {
        assertTrue(Serializer.builder().put("key", "value").clear().build().isEmpty());
    }

    @Test
    void editingKeepsTheOriginal() {
        val original = Yaml.from(Map.of("port", 8080));
        val edited = Serializer.builder(original).put("port", 9090).put("host", "x").build();
        assertEquals(8080, original.asInt("port"));
        assertNull(original.asString("host"));
        assertEquals(9090, edited.asInt("port"));
    }
}
