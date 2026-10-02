package br.commons.htmx;

import br.commons.http.message.HTTPRequest;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * The request headers htmx sends (https://htmx.org/reference/#request_headers), read from an
 * {@link HTTPRequest}: whether the request came from htmx, and from which element, target and page.
 *
 * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
 */
@NullMarked
public final class HtmxRequest {

    private final HTTPRequest request;

    private HtmxRequest(HTTPRequest request) {
        this.request = request;
    }

    public static HtmxRequest of(HTTPRequest request) {
        return new HtmxRequest(request);
    }

    /**
     * {@code HX-Request}: the request came from htmx (render a fragment, not the whole page).
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public boolean isHtmx() { return flag("HX-Request"); }

    /**
     * {@code HX-Boosted}: the request came from an element with {@code hx-boost}.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public boolean boosted() { return flag("HX-Boosted"); }

    /**
     * {@code HX-History-Restore-Request}: htmx restores history after a cache miss (render the whole page).
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public boolean historyRestore() { return flag("HX-History-Restore-Request"); }

    /**
     * {@code HX-Current-URL}: the URL of the browser.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public Optional<String> currentUrl() { return value("HX-Current-URL"); }

    /**
     * {@code HX-Prompt}: the answer of the person to {@code hx-prompt}.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public Optional<String> prompt() { return value("HX-Prompt"); }

    /**
     * {@code HX-Target}: the {@code id} of the target element, when it has one.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public Optional<String> target() { return value("HX-Target"); }

    /**
     * {@code HX-Trigger}: the {@code id} of the triggering element, when it has one.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public Optional<String> trigger() { return value("HX-Trigger"); }

    /**
     * {@code HX-Trigger-Name}: the {@code name} of the triggering element, when it has one.
     *
     * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
     */
    public Optional<String> triggerName() { return value("HX-Trigger-Name"); }

    private boolean flag(String header) {
        return "true".equals(request.header(header));
    }

    private Optional<String> value(String header) {
        return Optional.ofNullable(request.header(header));
    }
}
