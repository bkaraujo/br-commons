package br.commons.web.router;

import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.web.RouteHandler;
import br.commons.web.WebResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteTest {

    private static final RouteHandler OK = request -> WebResponse.noContent();

    private static Route get(String template) {
        return Route.get(template, MediaType.APPLICATION_JSON, Void.class, true, OK).getOrThrow();
    }

    @Test
    void verbsWithoutBodyHaveNoConsumes() {
        val get = get("/x");
        assertEquals(HTTPMethod.GET, get.method());
        assertNull(get.consumes());
        assertEquals(Void.class, get.requestType());

        val delete = Route.delete("/x", MediaType.APPLICATION_JSON, String.class, true, OK).getOrThrow();
        assertEquals(HTTPMethod.DELETE, delete.method());
        assertNull(delete.consumes());
        assertEquals(String.class, delete.responseType());
    }

    @Test
    void verbsWithBodyKeepWhatTheyConsume() {
        val post = Route.post("/x", MediaType.URL_ENCODED, MediaType.TEXT_HTML, String.class, Integer.class, false, OK).getOrThrow();
        assertEquals(HTTPMethod.POST, post.method());
        assertEquals(MediaType.URL_ENCODED, post.consumes());
        assertEquals(MediaType.TEXT_HTML, post.produces());
        assertEquals(String.class, post.requestType());
        assertEquals(Integer.class, post.responseType());
        assertFalse(post.authenticated());

        assertEquals(HTTPMethod.PUT, Route.put("/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK).getOrThrow().method());
        assertEquals(HTTPMethod.PATCH, Route.patch("/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK).getOrThrow().method());
    }

    @Test
    void templateBecomesAPatternWithNamedParameters() {
        val route = get("/a/{id}/b/{subId}");
        assertEquals(List.of("id", "subId"), route.paramNames());

        val matcher = route.pattern().matcher("/a/1/b/2");
        assertTrue(matcher.matches());
        assertEquals("1", matcher.group("id"));
        assertEquals("2", matcher.group("subId"));
        assertFalse(route.pattern().matcher("/a/1/b").matches());
        assertFalse(route.pattern().matcher("/a/1/b/2/3").matches());
        assertFalse(route.pattern().matcher("/a//b/2").matches());
    }

    @Test
    void literalsAreNotRegexAndSlashesAreNormalized() {
        assertTrue(get("/a.b/c+d").pattern().matcher("/a.b/c+d").matches());
        assertFalse(get("/a.b").pattern().matcher("/axb").matches());
        assertTrue(get("//a//b/").pattern().matcher("/a/b").matches());
        assertTrue(get("/").pattern().matcher("/").matches());
        assertTrue(get("").pattern().matcher("/").matches());
        assertEquals(List.of(), get("/").paramNames());
    }

    @Test
    void aBracedSegmentThatIsNotAWholeParameterIsLiteral() {
        val route = get("/{a/b}");
        assertTrue(route.paramNames().isEmpty());
        assertTrue(route.pattern().matcher("/{a/b}").matches());
        assertTrue(get("/x{id}").pattern().matcher("/x{id}").matches());
    }

    @Test
    void invalidParameterNamesFail() {
        for (val template : List.of("/{order-id}", "/{}", "/{1id}", "/{id_}", "/{çao}", "/{a b}")) {
            assertTrue(Route.get(template, MediaType.APPLICATION_JSON, Void.class, true, OK).isFailure(), template);
        }
        assertTrue(Route.get("/{a}/{b2}/{C3}", MediaType.APPLICATION_JSON, Void.class, true, OK).isSuccess());
    }

    @Test
    void repeatedParameterNamesFail() {
        assertTrue(Route.get("/a/{id}/b/{id}", MediaType.APPLICATION_JSON, Void.class, true, OK).isFailure());
    }

    @Test
    void paramNamesAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> get("/a/{id}").paramNames().add("x"));
    }
}
