package br.commons.web.filter;

import br.commons.Logger;
import br.commons.logger.LogLevel;
import br.commons.logger.channel.ConsoleChannel;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {

    private static final HTTPHandler NEXT = _ -> HTTPServerResponse.status(HTTPStatus.OK);

    private final List<String> captured = new CopyOnWriteArrayList<>();

    @BeforeEach
    void capture() {
        Logger.channel(captured::add);
        Logger.level(LogLevel.INFO);
    }

    @AfterEach
    void restore() {
        Logger.channel(new ConsoleChannel());
    }

    private boolean logs(RequestLoggingFilter filter, String path) {
        captured.clear();
        filter.apply(new HTTPServerRequest("", HTTPMethod.GET, path, Map.of(), new byte[0]), NEXT);
        return captured.stream().anyMatch(line -> line.contains("GET " + path + " -> 200"));
    }

    /** Regression (R7): any path that merely contained "static" was silenced. */
    @Test
    void defaultsSkipOnlyPrefixesAndSuffixes() {
        val filter = new RequestLoggingFilter();
        assertFalse(logs(filter, "/static/css/app.css"));
        assertFalse(logs(filter, "/.well-known/security.txt"));
        assertFalse(logs(filter, "/site.webmanifest"));
        assertTrue(logs(filter, "/api/staticReports"));
        assertTrue(logs(filter, "/reports/static"));
        assertTrue(logs(filter, "/login"));
    }

    @Test
    void listsAreConfigurable() {
        val filter = new RequestLoggingFilter(List.of("/health"), List.of(".png"));
        assertFalse(logs(filter, "/health/live"));
        assertFalse(logs(filter, "/img/logo.png"));
        assertTrue(logs(filter, "/static/css/app.css"));
    }

    /** A form sent by GET, or a token, stays out of the log: only the path is written. */
    @Test
    void queryIsNotLogged() {
        captured.clear();
        new RequestLoggingFilter().apply(new HTTPServerRequest("", HTTPMethod.GET, "/split?token=secret&value=10",
                Map.of(), new byte[0]), NEXT);
        assertEquals(List.of(true), captured.stream().map(line -> line.contains("GET /split -> 200")).toList());
        assertFalse(captured.stream().anyMatch(line -> line.contains("secret")));
    }
}
