package br.commons.business;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BusinessErrorTest {

    /** Regression (C17): records compared the {@code args} array by reference. */
    @Test
    void argsCompareByContent() {
        assertEquals(new BusinessError.NotFound("order.missing", 7, "x"), new BusinessError.NotFound("order.missing", 7, "x"));
        assertEquals(new BusinessError.Conflict("k", 1).hashCode(), new BusinessError.Conflict("k", 1).hashCode());
        assertNotEquals(new BusinessError.Validation("k", 1), new BusinessError.Validation("k", 2));
        assertNotEquals(new BusinessError.Validation("k", 1), new BusinessError.BusinessRule("k", 1));
        assertEquals("NotFound[key=k, args=[1, a]]", new BusinessError.NotFound("k", 1, "a").toString());
    }
}
