package br.commons.lang.htmx;

import br.commons.lang.Json;
import br.commons.protocol.http.message.HTTPResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The response headers htmx reads (https://htmx.org/reference/#response_headers), written on an
 * {@link HTTPResponse}: client-side navigation, history, a different swap or target, and events to trigger.
 *
 * <pre>{@code
 * HtmxResponse.of(WebResponse.html(HTTPStatus.OK, fragment)).retarget("#errors").reswap(HxSwap.INNER_HTML).response()
 * }</pre>
 *
 * @see <a href="https://htmx.org/reference/#response_headers">htmx: response headers</a>
 */
@NullMarked
public final class HtmxResponse {

    private HTTPResponse response;

    /** Events per header ({@code HX-Trigger}, {@code -After-Swap}, {@code -After-Settle}): calls add up. */
    private final Map<String, Map<String, Object>> events = new LinkedHashMap<>();

    /** Detail of an event triggered by name only. */
    private static final Object NO_DETAIL = new Object();

    private HtmxResponse(HTTPResponse response) {
        this.response = response;
    }

    public static HtmxResponse of(HTTPResponse response) {
        return new HtmxResponse(response);
    }

    public HTTPResponse response() {
        return response;
    }

    // ---------- navigation and history

    /**
     * {@code HX-Location}: client-side navigation to the path, without a full reload.
     *
     * @see <a href="https://htmx.org/headers/hx-location/">htmx: HX-Location</a>
     */
    public HtmxResponse location(String path) { return header("HX-Location", path); }

    /**
     * {@code HX-Redirect}: client-side redirect with a full reload.
     *
     * @see <a href="https://htmx.org/headers/hx-redirect/">htmx: HX-Redirect</a>
     */
    public HtmxResponse redirect(String url) { return header("HX-Redirect", url); }

    /**
     * {@code HX-Refresh}: full refresh of the page.
     *
     * @see <a href="https://htmx.org/reference/#response_headers">htmx: response headers</a>
     */
    public HtmxResponse refresh() { return header("HX-Refresh", "true"); }

    /**
     * {@code HX-Push-Url}: pushes the URL into the history.
     *
     * @see <a href="https://htmx.org/headers/hx-push-url/">htmx: HX-Push-Url</a>
     */
    public HtmxResponse pushUrl(String url) { return header("HX-Push-Url", url); }

    /**
     * {@code HX-Push-Url: false}: does not push, even if the element asks to.
     *
     * @see <a href="https://htmx.org/headers/hx-push-url/">htmx: HX-Push-Url</a>
     */
    public HtmxResponse noPushUrl() { return header("HX-Push-Url", "false"); }

    /**
     * {@code HX-Replace-Url}: replaces the URL in the location bar.
     *
     * @see <a href="https://htmx.org/headers/hx-replace-url/">htmx: HX-Replace-Url</a>
     */
    public HtmxResponse replaceUrl(String url) { return header("HX-Replace-Url", url); }

    /**
     * {@code HX-Replace-Url: false}: does not replace, even if the element asks to.
     *
     * @see <a href="https://htmx.org/headers/hx-replace-url/">htmx: HX-Replace-Url</a>
     */
    public HtmxResponse noReplaceUrl() { return header("HX-Replace-Url", "false"); }

    // ---------- swap

    /**
     * {@code HX-Reswap}: swaps differently from the element's {@code hx-swap}.
     *
     * @see <a href="https://htmx.org/reference/#response_headers">htmx: response headers</a>
     */
    public HtmxResponse reswap(HxSwap style) { return header("HX-Reswap", style.token()); }

    public HtmxResponse reswap(SwapSpec spec) { return header("HX-Reswap", spec.token()); }

    /**
     * {@code HX-Retarget}: swaps into the element matching the selector, not the element's target.
     *
     * @see <a href="https://htmx.org/reference/#response_headers">htmx: response headers</a>
     */
    public HtmxResponse retarget(String selector) { return header("HX-Retarget", selector); }

    /**
     * {@code HX-Reselect}: swaps this part of the response, not the element's {@code hx-select}.
     *
     * @see <a href="https://htmx.org/reference/#response_headers">htmx: response headers</a>
     */
    public HtmxResponse reselect(String selector) { return header("HX-Reselect", selector); }

    // ---------- client-side events

    /**
     * {@code HX-Trigger}: events triggered as soon as the response arrives.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse trigger(String... events) { return trigger("HX-Trigger", events); }

    /**
     * {@code HX-Trigger} with the detail of each event, written as JSON by {@link Json#write}.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse trigger(Map<String, ?> events) { return trigger("HX-Trigger", events); }

    /**
     * {@code HX-Trigger-After-Swap}: events triggered after the swap.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse triggerAfterSwap(String... events) {
        return trigger("HX-Trigger-After-Swap", events);
    }

    public HtmxResponse triggerAfterSwap(Map<String, ?> events) {
        return trigger("HX-Trigger-After-Swap", events);
    }

    /**
     * {@code HX-Trigger-After-Settle}: events triggered after the settle step.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse triggerAfterSettle(String... events) {
        return trigger("HX-Trigger-After-Settle", events);
    }

    public HtmxResponse triggerAfterSettle(Map<String, ?> events) {
        return trigger("HX-Trigger-After-Settle", events);
    }

    private HtmxResponse trigger(String header, String... names) {
        val accumulated = events.computeIfAbsent(header, _ -> new LinkedHashMap<>());
        for (val name : names) accumulated.put(name, NO_DETAIL);
        return writeEvents(header, accumulated);
    }

    private HtmxResponse trigger(String header, Map<String, ?> detailed) {
        val accumulated = events.computeIfAbsent(header, _ -> new LinkedHashMap<>());
        accumulated.putAll(detailed);
        return writeEvents(header, accumulated);
    }

    /**
     * Rewrites the header with every event so far: a name list while no event has detail, JSON otherwise (an event
     * without detail goes as {@code ""}, which htmx accepts).
     */
    private HtmxResponse writeEvents(String header, Map<String, Object> accumulated) {
        if (accumulated.values().stream().allMatch(detail -> detail == NO_DETAIL)) {
            return header(header, String.join(", ", accumulated.keySet()));
        }
        val json = new LinkedHashMap<String, Object>();
        accumulated.forEach((name, detail) -> json.put(name, detail == NO_DETAIL ? "" : detail));
        return header(header, Json.write(json));
    }

    private HtmxResponse header(String name, String value) {
        response = response.header(name, value);
        return this;
    }
}
