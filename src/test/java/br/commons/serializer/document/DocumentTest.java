package br.commons.serializer.document;

import br.commons.lang.Json;
import br.commons.serializer.Serializer;
import br.commons.lang.Yaml;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The common representation: immutable, validated on input, read from cache. */
class DocumentTest {

    @Test
    void sameJsonTextGivesTheSameDocument() {
        assertSame(Json.from("{\"a\":1}"), Json.from("{\"a\":1}"));
    }

    @Test
    void sameValueComesFromTheCache() {
        val doc = Yaml.from(Map.of("db", Map.of("host", "x")));
        assertSame(doc.subtree("db"), doc.subtree("db"));
        assertSame(doc.list("none"), doc.list("none"));
    }

    @Test
    void copiesTheInputSoLaterChangesDoNotLeak() {
        val source = new HashMap<String, Object>();
        source.put("a", "1");
        val doc = Yaml.from(source);
        source.put("a", "2");
        assertEquals("1", doc.asString("a"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void treeIsImmutable() {
        val tree = (Map<String, Object>) Document.unwrap(Yaml.from(Map.of("a", List.of(1)))).tree();
        assertThrows(UnsupportedOperationException.class, () -> tree.put("b", 2));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) tree.get("a")).add(2));
    }

    @Test
    void rejectsKeysThatAreNotText() {
        assertThrows(IllegalArgumentException.class, () -> Document.of(Map.of(1, "x"), null, Policy.CONFIG));
    }

    @Test
    void rejectsTrailingJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.from("{} x"));
    }

    @Test
    void jsonWritesADocument() {
        val doc = Serializer.builder().put("a.b", 1).build();
        assertEquals("{\"a\":{\"b\":1}}", Json.write(doc));
        assertTrue(Json.from(Json.write(doc)).contains("a.b"));
    }
}
