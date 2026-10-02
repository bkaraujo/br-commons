package br.commons.serializer;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
