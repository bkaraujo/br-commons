package br.commons.logger;

import br.commons.Logger;
import br.commons.logger.channel.ConsoleChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public level gate — what the hot path queries before building an expensive argument
 * (stopwatch, stack walk, formatting), since the gate inside {@code Logger.debug} only runs
 * after the argument has been evaluated.
 */
class LoggerEnabledGateTest {

    private static final String CACHE_CLASS = "br.commons.cache.AbstractCache";
    private static final String CACHE_PKG = "br.commons.cache";

    @BeforeEach
    void setUp() {
        Logger.clearPackageFilters();
        Logger.level(LogLevel.INFO);
    }

    @AfterEach
    void tearDown() {
        Logger.clearPackageFilters();
        Logger.level(LogLevel.INFO);
        Logger.channel(new ConsoleChannel());
    }

    @Test
    void followsGlobalLevelWhenNoPackageFilter() {
        assertFalse(Logger.enabled(LogLevel.DEBUG, CACHE_CLASS));
        assertTrue(Logger.enabled(LogLevel.WARN, CACHE_CLASS));

        Logger.level(LogLevel.DEBUG);
        assertTrue(Logger.enabled(LogLevel.DEBUG, CACHE_CLASS));
    }

    @Test
    void packageOverrideWinsOverRoot() {
        Logger.level(LogLevel.WARN);
        Logger.level(CACHE_PKG, LogLevel.DEBUG);

        assertTrue(Logger.enabled(LogLevel.DEBUG, CACHE_CLASS), "override do pacote reabre DEBUG");
        assertFalse(Logger.enabled(LogLevel.DEBUG, "br.commons.platform.NativeCache"),
                "pacote sem override segue o root WARN");
    }

    @Test
    void packageOverrideCanBeMoreRestrictive() {
        Logger.level(LogLevel.DEBUG);
        Logger.level(CACHE_PKG, LogLevel.WARN);

        assertFalse(Logger.enabled(LogLevel.DEBUG, CACHE_CLASS));
        assertTrue(Logger.enabled(LogLevel.WARN, CACHE_CLASS));
    }

    @Test
    void offSilencesEverything() {
        Logger.level(LogLevel.OFF);

        assertFalse(Logger.enabled(LogLevel.FATAL, CACHE_CLASS));
        assertFalse(Logger.enabled(LogLevel.DEBUG, CACHE_CLASS));
    }
}
