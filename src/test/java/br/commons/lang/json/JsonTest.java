package br.commons.lang.json;

import br.commons.lang.Json;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    enum Kind { DEBIT, CREDIT }

    record Entry(UUID id, String description, Kind kind, int cents) {}

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void writesAndReadsRecords() {
        val json = Json.write(new Entry(ID, "PADARIA", Kind.DEBIT, 1250));
        assertEquals("{\"id\":\"" + ID + "\",\"description\":\"PADARIA\",\"kind\":\"DEBIT\",\"cents\":1250}", json);
        assertEquals(new Entry(ID, "PADARIA", Kind.DEBIT, 1250), Json.from(json).as(Entry.class));
        assertEquals("PADARIA", Json.from(json).asString("description"));
    }

    @Test
    void readsTopLevelArrays() {
        val json = Json.write(List.of(new Entry(ID, "A", Kind.CREDIT, 1), new Entry(ID, "B", Kind.DEBIT, 2)));
        assertEquals(List.of(new Entry(ID, "A", Kind.CREDIT, 1), new Entry(ID, "B", Kind.DEBIT, 2)), Json.from(json).asList(Entry.class));
    }

    @Test
    void emptyDocumentHasNoKeys() {
        assertTrue(Json.from("{}").isEmpty());
        assertTrue(Json.from("[]").isEmpty());
        assertFalse(Json.from("{\"a\":1}").isEmpty());
    }

    /** What {@link Serializer} gives both formats: dotted keys, scalars, defaults and enums. */
    @Test
    void readsKeysScalarsAndEnumsLikeYaml() {
        val json = Json.from("{\"server\":{\"port\":8080,\"debug\":true,\"ratio\":0.5,\"tags\":[\"a\",\"b\"]},"
                + "\"kind\":\"CREDIT\",\"other\":\"NOPE\"}");
        assertTrue(json.contains("server.port"));
        assertTrue(json.contains("server.tags.1"));
        assertFalse(json.contains("server.none"));
        assertEquals(8080, json.asInt("server.port"));
        assertEquals(8080L, json.asLong("server.port"));
        assertEquals(true, json.asBoolean("server.debug"));
        assertEquals(0.5, json.asDouble("server.ratio"));
        assertEquals("b", json.asString("server.tags.1"));
        assertEquals(7, json.asInt("server.missing", 7));
        assertEquals(Kind.CREDIT, json.asEnum("kind", Kind.class));
        assertNull(json.asEnum("other", Kind.class));
        assertNull(json.asEnum("missing", Kind.class));
    }
}
