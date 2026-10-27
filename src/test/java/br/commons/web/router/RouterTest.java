package br.commons.web.router;

import br.commons.Result;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.web.RouteHandler;
import br.commons.web.WebResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    private static final RouteHandler OK = _ -> WebResponse.noContent();

    private static Result<Route, String> get(String template) {
        return Route.get(template, MediaType.APPLICATION_JSON, Void.class, true, OK);
    }

    @Test
    void withoutRulesEveryVerbButOptionsRegisters() {
        val router = new Router();
        assertTrue(router.register(Route.put("/api/x/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK)).isSuccess());
        assertNotNull(router.match(HTTPMethod.PUT, "/api/x/1"));
    }

    @Test
    void clientRuleFailsTheRegistration() {
        val router = new Router((method, _) -> method == HTTPMethod.DELETE ? Result.failure("sem DELETE") : Result.success());
        val registered = router.register(Route.delete("/api/x/{id}", MediaType.APPLICATION_JSON, Void.class, true, OK));
        assertEquals(new Result.Failure<Route, String>("sem DELETE"), registered);
        assertNull(router.allowHeader("/api/x/1"));
        assertNull(router.match(HTTPMethod.DELETE, "/api/x/1"));
    }

    @Test
    void optionsCannotBeRegistered() {
        val router = new Router();
        val options = Route.of(HTTPMethod.OPTIONS, "/api/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK);
        assertTrue(router.register(options).isFailure());
    }

    @Test
    void aFailedBuildPassesThroughTheRegistration() {
        val router = new Router();
        val registered = router.register(get("/orders/{order-id}"));
        assertTrue(registered.isFailure());
        assertTrue(router.routes().isEmpty());
    }

    @Test
    void allowIsCachedAtRegistrationAndSumsTemplatesOfTheSameShape() {
        val router = new Router();
        router.register(get("/api/x/{id}")).getOrThrow();
        router.register(Route.patch("/api/x/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK)).getOrThrow();
        router.register(Route.delete("/api/x/{key}", MediaType.APPLICATION_JSON, Void.class, true, OK)).getOrThrow();
        router.register(Route.post("/api/x", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Void.class, Void.class, true, OK)).getOrThrow();

        assertEquals("GET, PATCH, DELETE, OPTIONS", router.allowHeader("/api/x/1"));
        assertEquals("POST, OPTIONS", router.allowHeader("/api/x"));
        assertNull(router.allowHeader("/api/y"));
    }

    /** Regression (R6): registration after the server start was accepted on a router read without locks. */
    @Test
    void frozenRouterRefusesRegistration() {
        val router = new Router();
        router.freeze();
        assertTrue(router.register(get("/late")).isFailure());
        assertNull(router.match(HTTPMethod.GET, "/late"));
    }

    @Test
    void matchScansOnlyTheVerbOfTheRequest() {
        val router = new Router();
        router.register(get("/api/x")).getOrThrow();
        assertNotNull(router.match(HTTPMethod.GET, "/api/x"));
        assertNull(router.match(HTTPMethod.POST, "/api/x"));
        assertNull(router.match(HTTPMethod.OPTIONS, "/api/x"));
    }

    @Test
    void routeWithoutParametersMatchesItsNormalizedPathOnly() {
        val router = new Router();
        router.register(get("//api//x/")).getOrThrow();
        router.register(get("/")).getOrThrow();

        val match = router.match(HTTPMethod.GET, "/api/x");
        assertNotNull(match);
        assertEquals("//api//x/", match.route().template());
        assertTrue(match.pathParams().isEmpty());
        assertNotNull(router.match(HTTPMethod.GET, "/"));
        assertNull(router.match(HTTPMethod.GET, "/api/x/y"));
        assertNull(router.match(HTTPMethod.GET, "/api"));
    }

    @Test
    void parametersAreReadByPositionInTheOrderOfTheTemplate() {
        val router = new Router();
        router.register(get("/a/{first}/b/{second}/c/{third}")).getOrThrow();
        router.register(get("/one/{id}")).getOrThrow();

        assertEquals(Map.of("first", "1", "second", "2", "third", "3"),
                matched(router, HTTPMethod.GET, "/a/1/b/2/c/3").pathParams());
        assertEquals(Map.of("id", "7"), matched(router, HTTPMethod.GET, "/one/7").pathParams());
        assertNull(router.match(HTTPMethod.GET, "/one/7/8"));
        assertNull(router.match(HTTPMethod.GET, "/one/"));
    }

    @Test
    void theFirstRegisteredRouteThatMatchesWins() {
        val router = new Router();
        router.register(get("/x/{id}")).getOrThrow();
        router.register(get("/x/new")).getOrThrow();
        assertEquals("/x/{id}", matched(router, HTTPMethod.GET, "/x/new").route().template());

        val inOrder = new Router();
        inOrder.register(get("/x/new")).getOrThrow();
        inOrder.register(get("/x/{id}")).getOrThrow();
        assertEquals("/x/new", matched(inOrder, HTTPMethod.GET, "/x/new").route().template());
        assertEquals("/x/{id}", matched(inOrder, HTTPMethod.GET, "/x/7").route().template());
    }

    @Test
    void theRouteKeepsTheAuthenticationFlagItWasBuiltWith() {
        val router = new Router();
        router.register(Route.post("/login", MediaType.URL_ENCODED, MediaType.APPLICATION_JSON, Void.class, Void.class, false, OK)).getOrThrow();
        router.register(get("/me")).getOrThrow();
        assertFalse(matched(router, HTTPMethod.POST, "/login").route().authenticated());
        assertTrue(matched(router, HTTPMethod.GET, "/me").route().authenticated());
    }

    private static Match matched(Router router, HTTPMethod method, String path) {
        return Objects.requireNonNull(router.match(method, path));
    }
}
