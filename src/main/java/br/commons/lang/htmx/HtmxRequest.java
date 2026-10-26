package br.commons.lang.htmx;

import br.commons.protocol.http.message.HTTPRequest;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * The <a href="https://htmx.org/reference/#request_headers">request headers htmx sends</a>, read from an
 * {@link HTTPRequest}: whether the request came from htmx, and from which element, target and page.
 *
 * @see <a href="https://htmx.org/reference/#request_headers">htmx: request headers</a>
 */
@NullMarked
public final class HtmxRequest {

    /**
     * The headers that change the answer of a URL read by {@link #navigation()} and {@link #fragment()}: the page, its
     * content or a part of it. Goes in {@code Vary}, so a cache never serves one for the other.
     */
    public static final String VARY = "HX-Request, HX-Boosted, HX-History-Restore-Request";

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
     * A boosted navigation ({@code hx-boost}) and not a history restore (which swaps the whole body): the page answers
     * only what changes in it, its content.
     */
    public boolean navigation() { return boosted() && !historyRestore(); }

    /**
     * htmx asked for a part of the page: neither a boosted navigation ({@link #navigation()}) nor a history restore
     * (which needs the whole page).
     */
    public boolean fragment() { return isHtmx() && !boosted() && !historyRestore(); }

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
