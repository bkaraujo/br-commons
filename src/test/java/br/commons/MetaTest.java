package br.commons;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class MetaTest {

    // ── fqn ───────────────────────────────────────────────────────────────────

    @Test
    void fqn_fromObject_returnsFullClassName() {
        assertEquals("java.lang.String", Meta.fqn("hello"));
        assertEquals("java.lang.Integer", Meta.fqn(42));
    }

    @Test
    void fqn_fromClass_returnsFullClassName() {
        assertEquals("java.lang.String", Meta.fqn(String.class));
        assertEquals("java.lang.Integer", Meta.fqn(Integer.class));
    }

    // ── assignable ────────────────────────────────────────────────────────────

    @Test
    void assignable_sameClass() {
        assertTrue(Meta.assignable(String.class, String.class));
    }

    @Test
    void assignable_subclassToSuperclass() {
        assertTrue(Meta.assignable(Integer.class, Number.class));
    }

    @Test
    void assignable_unrelated() {
        assertFalse(Meta.assignable(String.class, Integer.class));
    }

    @Test
    void assignable_nullClass_returnsFalse() {
        assertFalse(Meta.assignable((Class<?>) null, String.class));
        assertFalse(Meta.assignable(String.class, null));
    }

    @Test
    void assignable_nullObject_returnsFalse() {
        assertFalse(Meta.assignable((Object) null, String.class));
    }

    @Test
    void assignable_objectToType_byInstance() {
        assertTrue(Meta.assignable("hello", String.class));
        assertTrue(Meta.assignable(42, Number.class));
    }

    // ── evaluate ──────────────────────────────────────────────────────────────

    @Test
    void evaluate_plainValues_unchanged() {
        Object[] args = {"hello", 42, true};
        val result = Meta.evaluate(args);
        assertArrayEquals(args, result);
    }

    @Test
    void evaluate_supplierArgs_resolved() {
        Supplier<Object> s = () -> "from supplier";
        val result = Meta.evaluate(new Object[]{s, "plain"});
        assertEquals("from supplier", result[0]);
        assertEquals("plain", result[1]);
    }

    @Test
    void evaluate_mixedArgs() {
        val result = Meta.evaluate(new Object[]{(Supplier<Object>) () -> 99, "x"});
        assertEquals(99, result[0]);
        assertEquals("x", result[1]);
    }

    // ── fields ────────────────────────────────────────────────────────────────

    @Test
    void field_existingField_found() {
        val obj = new TestContainer();
        val field = Meta.field(obj, "value");
        assertNotNull(field);
        assertEquals("value", field.getName());
    }

    @Test
    void field_missingField_returnsNull() {
        assertNull(Meta.field(new TestContainer(), "nonExistent"));
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    void close_null_noException() {
        assertDoesNotThrow(() -> Meta.close(null));
    }

    @Test
    void close_closeable_callsClose() {
        val closed = new boolean[]{false};
        Meta.close(() -> closed[0] = true);
        assertTrue(closed[0]);
    }

    // helper
    static class TestContainer {
        public String value = "test";
    }

    public static final class Point {
        public final int x;
        public final String label;
        public Point(int x, String label) { this.x = x; this.label = label; }
        public Point(int x) { this(x, "none"); }
        public Point() { throw new IllegalStateException("boom"); }
    }

    /** Regression (C10): with args it looked up the no-arg constructor and ended the JVM. */
    @Test
    void instanceWithArgsPicksTheMatchingConstructor() {
        val two = Meta.instance(Point.class, 3, "a").get();
        assertEquals(3, two.x);
        assertEquals("a", two.label);
        assertEquals("none", Meta.instance(Point.class, 4).get().label);
        assertEquals(5, Meta.instance(Point.class, 5, null).get().x);
    }

    @Test
    void instanceWithArgsFailsInsteadOfExiting() {
        assertTrue(Meta.instance(Point.class, (Object) "wrong").isFailure()); // a lone String would pick instance(type, property)
        assertTrue(Meta.instance(Point.class, (Object) null).isFailure()); // int does not take null
        assertTrue(Meta.instance(Point.class, new Object[0]).isFailure());  // the constructor throws
    }
}
