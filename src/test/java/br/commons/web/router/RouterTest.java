package br.commons.web.router;

import br.commons.Result;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPMethod;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    @Test
    void withoutRulesEveryVerbButOptionsRegisters() {
        val router = new Router();
        assertTrue(router.put("/api/x/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, request -> WebResponse.noContent()).isSuccess());
        assertNotNull(router.match(HTTPMethod.PUT, "/api/x/1"));
    }

    @Test
    void clientRuleFailsTheRegistration() {
        val router = new Router((method, template) ->
                method == HTTPMethod.DELETE ? Result.failure("sem DELETE") : Result.success());
        val registered = router.delete("/api/x/{id}", MediaType.APPLICATION_JSON, Void.class, request -> WebResponse.noContent());
        assertEquals(new Result.Failure<Route, String>("sem DELETE"), registered);
        assertNull(router.allowHeader("/api/x/1"));
    }

    @Test
    void optionsCannotBeRegistered() {
        val router = new Router();
        assertTrue(router.register(HTTPMethod.OPTIONS, "/api/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, request -> WebResponse.noContent()).isFailure());
    }

    @Test
    void allowIsCachedAtRegistrationAndSumsTemplatesOfTheSameShape() {
        val router = new Router();
        router.get("/api/x/{id}", MediaType.APPLICATION_JSON, Void.class, request -> WebResponse.noContent()).getOrThrow();
        router.patch("/api/x/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, request -> WebResponse.noContent()).getOrThrow();
        router.delete("/api/x/{key}", MediaType.APPLICATION_JSON, Void.class, request -> WebResponse.noContent()).getOrThrow();
        router.post("/api/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, request -> WebResponse.noContent()).getOrThrow();

        assertEquals("GET, PATCH, DELETE, OPTIONS", router.allowHeader("/api/x/1"));
        assertEquals("POST, OPTIONS", router.allowHeader("/api/x"));
        assertNull(router.allowHeader("/api/y"));
    }

    /** Regression (R6): registration after the server start was accepted on a router read without locks. */
    @Test
    void frozenRouterRefusesRegistration() {
        val router = new Router();
        router.freeze();
        assertTrue(router.get("/late", MediaType.APPLICATION_JSON, Void.class, request -> WebResponse.noContent()).isFailure());
        assertNull(router.match(HTTPMethod.GET, "/late"));
    }
}
