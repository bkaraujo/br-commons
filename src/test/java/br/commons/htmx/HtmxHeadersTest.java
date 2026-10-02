package br.commons.htmx;

import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPMethod;
import br.commons.http.server.message.HTTPServerRequest;
import br.commons.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code HX-*} headers of the htmx reference, read from the request and written on the response. */
class HtmxHeadersTest {

    private static HtmxRequest request(Map<String, String> headers) {
        return HtmxRequest.of(new HTTPServerRequest("", HTTPMethod.GET, "/", Map.of(), headers, new byte[0]));
    }

    @Test
    void readsTheRequestHeaders() {
        val htmx = request(Map.of("hx-request", "true", "hx-boosted", "true", "hx-history-restore-request", "true",
                "hx-current-url", "https://br.dev/a", "hx-prompt", "sim", "hx-target", "list", "hx-trigger", "btn",
                "hx-trigger-name", "save"));
        assertTrue(htmx.isHtmx());
        assertTrue(htmx.boosted());
        assertTrue(htmx.historyRestore());
        assertEquals(Optional.of("https://br.dev/a"), htmx.currentUrl());
        assertEquals(Optional.of("sim"), htmx.prompt());
        assertEquals(Optional.of("list"), htmx.target());
        assertEquals(Optional.of("btn"), htmx.trigger());
        assertEquals(Optional.of("save"), htmx.triggerName());
    }

    @Test
    void aPlainRequestIsNotHtmx() {
        val plain = request(Map.of());
        assertFalse(plain.isHtmx());
        assertFalse(plain.boosted());
        assertFalse(plain.historyRestore());
        assertEquals(Optional.empty(), plain.target());
        assertFalse(request(Map.of("hx-request", "false")).isHtmx());
    }

    @Test
    void writesTheResponseHeaders() {
        val response = HtmxResponse.of(HTTPServerResponse.status(HTTPStatus.OK))
                .location("/l").redirect("/r").refresh().pushUrl("/p").replaceUrl("/u")
                .reswap(SwapSpec.of(HxSwap.OUTER_HTML).settle(Duration.ofMillis(10))).retarget("#t").reselect("#s")
                .trigger("saved", "counted").triggerAfterSwap("swapped").triggerAfterSettle("settled")
                .response();
        assertEquals("/l", response.header("HX-Location"));
        assertEquals("/r", response.header("HX-Redirect"));
        assertEquals("true", response.header("HX-Refresh"));
        assertEquals("/p", response.header("HX-Push-Url"));
        assertEquals("/u", response.header("HX-Replace-Url"));
        assertEquals("outerHTML settle:10ms", response.header("HX-Reswap"));
        assertEquals("#t", response.header("HX-Retarget"));
        assertEquals("#s", response.header("HX-Reselect"));
        assertEquals("saved, counted", response.header("HX-Trigger"));
        assertEquals("swapped", response.header("HX-Trigger-After-Swap"));
        assertEquals("settled", response.header("HX-Trigger-After-Settle"));
    }

    @Test
    void writesEventsWithDetailAndTheFalseValues() {
        Map<String, Object> events = new LinkedHashMap<>();
        events.put("saved", Map.of("id", 7));
        val response = HtmxResponse.of(HTTPServerResponse.status(HTTPStatus.OK))
                .trigger(events).triggerAfterSwap(Map.of("a", "x")).triggerAfterSettle(Map.of("b", true))
                .noPushUrl().noReplaceUrl().reswap(HxSwap.NONE).response();
        assertEquals("{\"saved\":{\"id\":7}}", response.header("HX-Trigger"));
        assertEquals("{\"a\":\"x\"}", response.header("HX-Trigger-After-Swap"));
        assertEquals("{\"b\":true}", response.header("HX-Trigger-After-Settle"));
        assertEquals("false", response.header("HX-Push-Url"));
        assertEquals("false", response.header("HX-Replace-Url"));
        assertEquals("none", response.header("HX-Reswap"));
    }
}
