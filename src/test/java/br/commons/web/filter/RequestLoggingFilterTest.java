package br.commons.web.filter;

import br.commons.Logger;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.HTTPHandler;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.protocol.http.server.message.HTTPServerResponse;
import br.commons.logger.LogLevel;
import br.commons.logger.channel.ConsoleChannel;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestLoggingFilterTest {

    private static final HTTPHandler NEXT = request -> HTTPServerResponse.status(HTTPStatus.OK);

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
        filter.apply(new HTTPServerRequest("", HTTPMethod.GET, path, Map.of(), Map.of(), new byte[0]), NEXT);
        return captured.stream().anyMatch(line -> line.contains("GET " + path + " -> 200"));
    }

    /** Regression (R7): any path that merely contained "static" was silenced. */
    @Test
    void defaultsSkipOnlyPrefixesAndSuffixes() {
        val filter = new RequestLoggingFilter();
        assertEquals(false, logs(filter, "/static/css/app.css"));
        assertEquals(false, logs(filter, "/.well-known/security.txt"));
        assertEquals(false, logs(filter, "/site.webmanifest"));
        assertEquals(true, logs(filter, "/api/staticReports"));
        assertEquals(true, logs(filter, "/reports/static"));
        assertEquals(true, logs(filter, "/login"));
    }

    @Test
    void listsAreConfigurable() {
        val filter = new RequestLoggingFilter(List.of("/health"), List.of(".png"));
        assertEquals(false, logs(filter, "/health/live"));
        assertEquals(false, logs(filter, "/img/logo.png"));
        assertEquals(true, logs(filter, "/static/css/app.css"));
    }
}
