package br.commons.http;

import br.commons.Result;
import br.commons.http.message.HTTPRequest;
import br.commons.logger.MDC;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State of an HTTP request in progress, keyed by the {@link HTTPRequest#requestId()} that the server
 * publishes in the MDC under {@link HTTPRequest#REQUEST_ID} — not its own {@code ThreadLocal}, and
 * distinct from the composition service locator in {@code br-commons-context}. Whoever opens closes:
 * {@code try (HTTPSession ignored = HTTPSession.open(request.requestId()))}.
 *
 * <p>The id is just the session key — its owner is the request. The framework does not know what the session
 * carries: each application writes and reads its own attributes.
 */
@NullMarked
public final class HTTPSession implements AutoCloseable {

    private static final Map<String, HTTPSession> SESSIONS = new ConcurrentHashMap<>();

    private final String requestId;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private HTTPSession(String requestId) {
        this.requestId = requestId;
    }

    public static HTTPSession open(String requestId) {
        val session = new HTTPSession(requestId);
        SESSIONS.put(requestId, session);
        return session;
    }

    /** The session of the request running on this thread. */
    @CheckReturnValue
    public static Result<HTTPSession, String> current() {
        val requestId = MDC.get(HTTPRequest.REQUEST_ID);
        if (requestId == null) return Result.failure("No HTTP request in progress");

        val session = SESSIONS.get(requestId);
        if (session == null) return Result.failure("No HTTP session for request " + requestId);
        return Result.success(session);
    }

    public void attribute(String key, Object value) {
        attributes.put(key, value);
    }

    @CheckReturnValue
    public <T> Result<T, String> attribute(String key, Class<T> type) {
        val value = attributes.get(key);
        if (value == null) return Result.failure("No session attribute '" + key + "'");
        if (!type.isInstance(value)) {
            return Result.failure("Session attribute '" + key + "' is " + value.getClass().getSimpleName() + ", not " + type.getSimpleName());
        }
        return Result.success(type.cast(value));
    }

    /**
     * Removes only this session: if another request opened one with the same id (client reusing the
     * {@code X-REQUEST-ID}), its one stays on.
     */
    @Override
    public void close() {
        SESSIONS.remove(requestId, this);
    }
}
