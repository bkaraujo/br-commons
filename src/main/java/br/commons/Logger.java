package br.commons;

import br.commons.logger.LogChannel;
import br.commons.logger.LogFilter;
import br.commons.logger.LogForwarder;
import br.commons.logger.LogLevel;
import br.commons.logger.bridge.JULBridgeHandler;
import br.commons.logger.channel.ConsoleChannel;
import br.commons.logger.forwarder.VerboseForwarder;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NullMarked
public abstract class Logger {
    private Logger() {}

    // Single forwarder that emits any level. The decision to emit/ignore is made by the gate
    // (blocked), based on the effective level per package — the forwarder never filters by level.
    private static final LogForwarder forwarder = new VerboseForwarder();

    private static final LogFilter filter = new LogFilter(LogLevel.INFO);

    /** Property that {@code java.util.logging.LogManager} reads to choose the implementation. */
    private static final String JUL_MANAGER_PROPERTY = "java.util.logging.manager";

    /** Literal name on purpose: referencing the class here would load — and initialize — JUL. */
    private static final String JBOSS_LOG_MANAGER = "org.jboss.logmanager.LogManager";

    /**
     * Elects JBoss's LogManager as JUL implementation when it is on the classpath and nobody
     * chose another via command line ({@code -Djava.util.logging.manager=...}, as
     * {@code quarkus:dev} and the surefire fork do).
     *
     * <p>{@code Class.forName} with {@code initialize=false} only loads: initializing the subclass
     * would initialize {@code java.util.logging.LogManager} (the superclass) and JUL would be stuck on the
     * default implementation — exactly what this method exists to avoid. Without the presence check,
     * pointing the property to a missing class would make JUL fail to load it.
     */
    private static void installJBossLogManager() {
        if (System.getProperty(JUL_MANAGER_PROPERTY) != null) return;

        try {
            Class.forName(JBOSS_LOG_MANAGER, false, Logger.class.getClassLoader());
            System.setProperty(JUL_MANAGER_PROPERTY, JBOSS_LOG_MANAGER);
        } catch (ClassNotFoundException | LinkageError e) {
            // No jboss-logmanager on the classpath: JUL proceeds with the default implementation.
        }
    }

    private static LogLevel level = LogLevel.INFO;
    public static LogLevel level() { return level; }
    public static void level(LogLevel desired) {
        level = desired;
        filter.level(desired);
    }

    private static final List<LogChannel> channels = new CopyOnWriteArrayList<>(List.of(new ConsoleChannel()));
    public static List<LogChannel> channels() { return channels; }
    public static void channel(LogChannel desired) { channels.clear(); channels.add(desired); }
    public static void addChannel(LogChannel channel) { channels.add(channel); }

    // Package-level filtering methods

    /**
     * Configures log level for a specific package.
     *
     * @param packageName Full package name (e.g: "br.framework.mcp")
     * @param desired     Log level for the package
     */
    public static void level(String packageName, LogLevel desired) {
        filter.setPackageLevel(packageName, desired);
    }

    /**
     * Returns the effective level for a package, considering hierarchical inheritance.
     *
     * @param packageName Full package name
     * @return Effective level for the package
     */
    public static LogLevel level(String packageName) {
        return filter.getEffectiveLevel(packageName);
    }

    /**
     * Clears all package-level configurations.
     */
    public static void clearPackageFilters() {
        filter.clearPackageFilters();
    }

    /**
     * Removes log level configuration for a specific package.
     *
     * @param packageName Package name to remove
     */
    public static void removePackageLevel(String packageName) {
        filter.removePackageLevel(packageName);
    }

    // Helper methods

    /**
     * Resolve the caller class name.
     * First checks externalCaller (for SLF4J bridge), then uses StackWalker.
     *
     * @return Caller class name
     */
    private static String resolveCallerClass() {
        val external = externalCaller.get();
        if (external != null) {
            return external;
        }
        return Meta.stackFrame(0).className();
    }

    /** Guard shared by log methods — the negation of {@link #enabled(LogLevel)}. */
    private static boolean blocked(LogLevel messageLevel) {
        return !enabled(messageLevel);
    }

    /**
     * Decides if a message at this level would be emitted. The effective level (package-level override, or the
     * global level as fallback) is the only authority — completely independent of the rootlogger
     * when there is a package override for the caller.
     *
     * <p>Without package filters, the gate uses only the global level and avoids the StackWalker (expensive). With
     * filters active, it resolves the caller and uses {@link LogFilter#getEffectiveLevel}. Since
     * {@code resolveCallerClass} skips infrastructure frames
     * ({@link br.commons.Meta#stackFrame}), calling it from here does not change the resolved caller.
     *
     * <p>It is public because the gate inside {@link #debug(String, Object...)} arrives too late for those who
     * pay dearly for the argument: in {@code Logger.debug(fmt, expensive())} the {@code expensive()} is evaluated when
     * pushing the varargs, before the call even starts. Timer, stack walk and heavy formatting
     * check here first.
     */
    public static boolean enabled(LogLevel messageLevel) {
        if (!filter.hasPackageFilters()) {
            return reaches(filter.level(), messageLevel);
        }
        return reaches(filter.getEffectiveLevel(resolveCallerClass()), messageLevel);
    }

    /**
     * Like {@link #enabled(LogLevel)}, but without StackWalker: the caller provides its own class, which
     * on its side is a constant. This is what makes the gate viable on the hot path — with package-level
     * filter active, resolving the caller alone costs a full stack traversal, and
     * {@link LogFilter#getEffectiveLevel} memoizes the level by class name.
     */
    public static boolean enabled(LogLevel messageLevel, String callerClass) {
        if (Strings.isEmpty(callerClass)) return enabled(messageLevel);

        if (!filter.hasPackageFilters()) {
            return reaches(filter.level(), messageLevel);
        }
        return reaches(filter.getEffectiveLevel(callerClass), messageLevel);
    }

    /**
     * Decides if {@code messageLevel} reaches the {@code threshold} level. OFF (ordinal 0, the smallest)
     * means complete silence — needs explicit handling, because ordinal comparison alone
     * would accept everything.
     */
    private static boolean reaches(LogLevel threshold, LogLevel messageLevel) {
        return threshold != LogLevel.OFF && messageLevel.ordinal >= threshold.ordinal;
    }

    /**
     * Helper to create lazy arguments for expensive operations.
     * Usage: Logger.debug("Value: %s", lazy(() -> expensiveOperation()))
     */
    public static Supplier<Object> lazy(Supplier<Object> supplier) {
        return supplier;
    }

    public static void verbose(String message, Object... args) {
        if (blocked(LogLevel.VERBOSE)) return;
        forwarder.verbose(() -> message.formatted(Meta.evaluate(args)));
    }

    public static void trace(String message, Object... args) {
        if (blocked(LogLevel.TRACE)) return;
        forwarder.trace(() -> message.formatted(Meta.evaluate(args)));
    }

    public static void debug(String message, Object... args) {
        if (blocked(LogLevel.DEBUG)) return;
        forwarder.debug(() -> message.formatted(Meta.evaluate(args)));
    }

    public static void info(String message, Object... args) {
        if (blocked(LogLevel.INFO)) return;
        forwarder.info(() -> message.formatted(Meta.evaluate(args)));
    }

    public static void warn(String message, Object... args) {
        if (blocked(LogLevel.WARN)) return;
        forwarder.warn(() -> message.formatted(Meta.evaluate(args)));
    }

    public static void error(String message, Object... args) {
        if (blocked(LogLevel.ERROR)) return;
        forwarder.error(() -> message.formatted(Meta.evaluate(args)));
    }

    // ========================================================================
    // Methods with Supplier<String> - total lazy
    // The Supplier is only evaluated if the forwarder processes the level
    // ========================================================================

    /**
     * Log VERBOSE with lazy message.
     * The Supplier is only evaluated if the forwarder processes VERBOSE.
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void verbose(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.VERBOSE)) return;
        forwarder.verbose(messageSupplier);
    }

    /**
     * Log TRACE with lazy message.
     * The Supplier is only evaluated if the forwarder processes TRACE.
     *
     * <p>
     * Usage:
     *
     * <pre>{@code
     * Logger.trace(() -> "Expensive: %s".formatted(expensiveOperation()));
     * }</pre>
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void trace(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.TRACE)) return;
        forwarder.trace(messageSupplier);
    }

    /**
     * Log DEBUG with lazy message.
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void debug(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.DEBUG)) return;
        forwarder.debug(messageSupplier);
    }

    /**
     * Log INFO with lazy message.
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void info(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.INFO)) return;
        forwarder.info(() -> messageSupplier.get());
    }

    /**
     * Log WARN with lazy message.
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void warn(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.WARN)) return;
        forwarder.warn(() -> messageSupplier.get());
    }

    /**
     * Log ERROR with lazy message.
     *
     * @param messageSupplier supplier that produces the message
     */
    public static void error(Supplier<String> messageSupplier) {
        if (blocked(LogLevel.ERROR)) return;
        forwarder.error(() -> messageSupplier.get());
    }

    public static void fatal(String message, Object... args) {
        fatal(() -> message.formatted(Meta.evaluate(args)));
    }

    /**
     * Like {@link #fatal(String, Object...)}, but without formatting — the message already comes ready. This is what
     * bridges and {@link #stackTrace} use: third-party text (or a stack trace) passed through the
     * varargs overload would be reinterpreted as a format string and any {@code %} would throw.
     */
    public static void fatal(Supplier<String> messageSupplier) {
        forwarder.fatal(() -> {
            val local = new StringBuilder(messageSupplier.get());
            local.append("\n");

            val stackTrace = Meta.stackFrame();
            for (val entry : stackTrace.subList(2, stackTrace.size())) {
                local.append("  ").append(entry).append("\n");
            }

            return local.toString();
        });
    }

    private static final Map<LogLevel, Consumer<Supplier<String>>> SINKS = Map.of(
            LogLevel.VERBOSE, Logger::verbose,
            LogLevel.TRACE, Logger::trace,
            LogLevel.DEBUG, Logger::debug,
            LogLevel.INFO, Logger::info,
            LogLevel.WARN, Logger::warn,
            LogLevel.ERROR, Logger::error,
            LogLevel.FATAL, Logger::fatal);

    public static void stackTrace(LogLevel level) {
        val message = new StringBuilder("\n\n");
        val stackTrace = Meta.stackFrame();
        for (val entry : stackTrace.subList(1, stackTrace.size())) {
            message.append(entry).append("\n");
        }

        val sink = SINKS.get(level);
        if (sink == null) {
            throw new IllegalStateException("Unexpected value: " + level);
        }
        val rendered = message.toString();
        sink.accept(() -> rendered);
    }

    // Internal methods for SLF4J bridge to specify caller class

    private static final ThreadLocal<@Nullable String> externalCaller = new ThreadLocal<>();

    // Runs after every static field above: the configuration below logs, and logging reads them.
    static {
        level(LogLevel.INFO);

        // Must come BEFORE JULBridgeHandler.install(): installing the handler calls
        // LogManager.getLogManager(), and the static block of java.util.logging.LogManager reads
        // java.util.logging.manager ONCE, at class initialization. Whoever initializes JUL
        // first decides the implementation forever — if the default is left, JBoss's LogManager
        // (required by Quarkus) no longer enters and JBossLoggerFinder can only complain:
        // "The LogManager accessed before the java.util.logging.manager system property was set".
        installJBossLogManager();

        JULBridgeHandler.install();

        // Global configuration
        val globalLevel = System.getProperty("judas.log.level");
        if (globalLevel != null) {
            try {
                level(LogLevel.valueOf(Strings.upper(globalLevel)));
            } catch (IllegalArgumentException e) {
                warn("Invalid log level in judas.log.level: %s", globalLevel);
            }
        }

        // Per-package configuration
        System.getProperties().forEach((key, value) -> {
            if (key instanceof String k && k.startsWith("judas.log.level.")) {
                val packageName = k.substring("judas.log.level.".length());
                try {
                    val pkgLevel = LogLevel.valueOf(Strings.upper(value));
                    level(packageName, pkgLevel);
                    info("Log level for package %s set to %s", packageName, pkgLevel);
                } catch (IllegalArgumentException e) {
                    warn("Invalid log level for package %s: %s", packageName, value);
                }
            }
        });
    }

    public static void verboseWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::verbose);
    }

    public static void traceWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::trace);
    }

    public static void debugWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::debug);
    }

    public static void infoWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::info);
    }

    public static void warnWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::warn);
    }

    public static void errorWithCaller(Supplier<String> c, Supplier<String> m) {
        withCaller(c, m, Logger::error);
    }

    @Nullable
    public static String externalCaller() {
        return externalCaller.get();
    }

    /**
     * The sink receives the {@link Supplier}, never the already-rendered {@code String}: with {@code
     * Consumer<String>} the {@code Logger::debug} references would resolve to the varargs overload, which
     * does {@code message.formatted(...)} — third-party message would become a format string and any
     * {@code %} in it would throw {@code IllegalFormatException} inside whoever logged. That's how
     * {@code io.netty.util.NetUtil}'s {@code Loopback interface: … 0:0:0:0:0:0:0:1%lo} would crash the entire
     * boot with {@code APP_LOGLEVEL_ROOT=VERBOSE}. Additionally, the message is only rendered if the
     * level allows it.
     */
    private static void withCaller(Supplier<String> caller, Supplier<String> message, Consumer<Supplier<String>> sink) {
        try {
            externalCaller.set(caller.get());
            sink.accept(message);
        } finally {
            externalCaller.remove();
        }
    }
}
