package br.commons;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TupleTest {

    @Test
    void constructor_storesNameAndValue() {
        val t = new Tuple("key", "val");
        assertEquals("key", t.name());
        assertEquals("val", t.value());
    }

    @Test
    void equality_sameFields() {
        val a = new Tuple("k", "v");
        val b = new Tuple("k", "v");
        assertEquals(a, b);
    }

    @Test
    void equality_differentFields() {
        assertNotEquals(new Tuple("k1", "v"), new Tuple("k2", "v"));
        assertNotEquals(new Tuple("k", "v1"), new Tuple("k", "v2"));
    }

    @Test
    void hashCode_sameForEqualTuples() {
        val a = new Tuple("k", "v");
        val b = new Tuple("k", "v");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_containsFields() {
        val s = new Tuple("myKey", "myVal").toString();
        assertTrue(s.contains("myKey"));
        assertTrue(s.contains("myVal"));
    }
}
