package br.commons.lang.htmx;

import br.commons.lang.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Strategy of {@code hx-sync} when another request is in flight.
 *
 * @see <a href="https://htmx.org/attributes/hx-sync/">htmx: hx-sync</a>
 */
@NullMarked
public enum HxSync implements Keyword {

    /** Ignores this request (the default). */
    DROP("drop"),

    /** Ignores this request, or aborts it if another one starts while it is in flight. */
    ABORT("abort"),

    /** Aborts the current request and replaces it with this one. */
    REPLACE("replace"),

    /** Queues the first request arriving during an active request. */
    QUEUE_FIRST("queue first"),

    /** Queues the last request arriving during an active request. */
    QUEUE_LAST("queue last"),

    /** Queues every request arriving during an active request. */
    QUEUE_ALL("queue all");

    private final String token;

    HxSync(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
