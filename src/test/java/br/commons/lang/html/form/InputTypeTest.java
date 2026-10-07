package br.commons.lang.html.form;

import br.commons.Result;
import br.commons.lang.html.form.InputKind;
import br.commons.lang.html.form.InputType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InputTypeTest {

    @Test
    void readsEachTypeLikeTheBrowserSubmitsIt() {
        assertTrue(InputType.EMAIL.accepts("a.b+c@mail.example.com"));
        assertFalse(InputType.EMAIL.accepts("a@"));
        assertTrue(InputType.URL.accepts("https://br.dev/x?y=1"));
        assertFalse(InputType.URL.accepts("/relative"));
        assertFalse(InputType.URL.accepts("http://bad host"));
        assertTrue(InputType.NUMBER.accepts("-1.5e3"));
        assertFalse(InputType.RANGE.accepts("1,5"));
        assertTrue(InputType.TIME.accepts("10:15"));
        assertTrue(InputType.DATETIME_LOCAL.accepts("2026-10-02T10:15"));
        assertTrue(InputType.MONTH.accepts("2026-10"));
        assertFalse(InputType.MONTH.accepts("2026-13"));
        assertTrue(InputType.WEEK.accepts("2026-W53"));
        assertFalse(InputType.WEEK.accepts("2027-W53"));
        assertFalse(InputType.WEEK.accepts("2026-W00"));
        assertFalse(InputType.WEEK.accepts("2026W40"));
        assertTrue(InputType.COLOR.accepts("#a0B1c2"));
        assertFalse(InputType.COLOR.accepts("red"));
        assertTrue(InputType.CHECKBOX.accepts("anything"));
    }

    @Test
    void readsValuesByMeaning() {
        assertEquals(Result.success(new BigDecimal("1.5")), InputType.NUMBER.read("1.5"));
        assertEquals(Result.success(LocalDate.of(2026, 10, 2)), InputType.DATE.read("2026-10-02"));
        assertEquals(Result.success("2026-W09"), InputType.WEEK.read("2026-W09"));
        assertEquals(Result.success("free"), InputType.TEXT.read("free"));
        assertTrue(InputType.DATE.read("x") instanceof Result.Failure<Comparable<?>, String>(var error)
                && error.contains("x"));
    }

    @Test
    void classifiesTypesAndKnowsTheirToken() {
        assertEquals("datetime-local", InputType.DATETIME_LOCAL.token());
        assertTrue(InputType.SEARCH.textual());
        assertFalse(InputType.SEARCH.ordered());
        assertTrue(InputType.MONTH.ordered());
        assertEquals(InputKind.OTHER, InputType.FILE.kind());
        assertFalse(InputType.COLOR.textual() || InputType.COLOR.ordered());
    }
}
