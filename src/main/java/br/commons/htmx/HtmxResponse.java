package br.commons.htmx;

import br.commons.http.message.HTTPResponse;
import br.commons.serializer.Json;
import org.jspecify.annotations.NullMarked;

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
    public HtmxResponse trigger(String... events) { return header("HX-Trigger", String.join(", ", events)); }

    /**
     * {@code HX-Trigger} with the detail of each event, written as JSON by {@link Json#write}.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse trigger(Map<String, ?> events) { return header("HX-Trigger", Json.write(events)); }

    /**
     * {@code HX-Trigger-After-Swap}: events triggered after the swap.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse triggerAfterSwap(String... events) {
        return header("HX-Trigger-After-Swap", String.join(", ", events));
    }

    public HtmxResponse triggerAfterSwap(Map<String, ?> events) {
        return header("HX-Trigger-After-Swap", Json.write(events));
    }

    /**
     * {@code HX-Trigger-After-Settle}: events triggered after the settle step.
     *
     * @see <a href="https://htmx.org/headers/hx-trigger/">htmx: HX-Trigger</a>
     */
    public HtmxResponse triggerAfterSettle(String... events) {
        return header("HX-Trigger-After-Settle", String.join(", ", events));
    }

    public HtmxResponse triggerAfterSettle(Map<String, ?> events) {
        return header("HX-Trigger-After-Settle", Json.write(events));
    }

    private HtmxResponse header(String name, String value) {
        response = response.header(name, value);
        return this;
    }
}
