package br.commons.http;

import br.commons.Result;
import br.commons.http.message.HTTPRequest;
import br.commons.logger.MDC;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HTTPSessionTest {

    @Test
    void currentFindsTheSessionOfTheRequestIdInTheMdcAndCloseRemovesIt() {
        MDC.push(HTTPRequest.REQUEST_ID, "req-1");
        try {
            try (HTTPSession session = HTTPSession.open("req-1")) {
                assertSame(session, HTTPSession.current().getOrThrow());
            }
            assertTrue(HTTPSession.current().isFailure());
        } finally {
            MDC.pop(HTTPRequest.REQUEST_ID);
        }
        assertTrue(HTTPSession.current().isFailure());
    }

    @Test
    void attributesAreTypedAndMissingOnesFail() {
        try (HTTPSession session = HTTPSession.open("req-2")) {
            session.attribute("count", 42);

            assertEquals(new Result.Success<Integer, String>(42), session.attribute("count", Integer.class));
            assertTrue(session.attribute("count", String.class).isFailure());
            assertTrue(session.attribute("absent", Integer.class).isFailure());
        }
    }

    @Test
    void closingAStaleSessionKeepsTheOneThatReusedItsId() {
        val stale = HTTPSession.open("req-3");
        try (HTTPSession fresh = HTTPSession.open("req-3")) {
            stale.close();
            MDC.push(HTTPRequest.REQUEST_ID, "req-3");
            assertSame(fresh, HTTPSession.current().getOrThrow());
        } finally {
            MDC.pop(HTTPRequest.REQUEST_ID);
        }
    }
}
