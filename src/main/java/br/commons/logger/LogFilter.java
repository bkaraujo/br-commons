package br.commons.logger;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Hierarchical filter system for configuring log level per package.
 *
 * <p>Allows configuring different log levels for different packages,
 * with support for hierarchical inheritance and caching for performance.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * LogFilter filter = new LogFilter(LogLevel.INFO);
 * filter.setPackageLevel("br.framework.mcp", LogLevel.DEBUG);
 *
 * // Inherits DEBUG from br.framework.mcp
 * LogLevel level = filter.getEffectiveLevel("br.framework.mcp.MCPServer");
 * }</pre>
 *
 * <h2>Thread-Safety:</h2>
 * This class is thread-safe. Reads and writes can occur concurrently.
 *
 * @since 1.0
 */
@NullMarked
public final class LogFilter {

    /**
     * Ordered map: package → level.
     * ConcurrentSkipListMap maintains lexicographic order and is thread-safe.
     */
    private final ConcurrentSkipListMap<String, LogLevel> packageFilters;

    /**
     * Cache: className → effective LogLevel.
     * Avoids recalculating hierarchy repeatedly.
     */
    private final ConcurrentHashMap<String, LogLevel> cache;

    /**
     * Global level (fallback).
     */
    private volatile LogLevel globalLevel;

    /**
     * Creates a new LogFilter with default level.
     *
     * @param defaultLevel Default log level for classes without specific configuration
     */
    public LogFilter(LogLevel defaultLevel) {
        this.packageFilters = new ConcurrentSkipListMap<>();
        this.cache = new ConcurrentHashMap<>();
        this.globalLevel = defaultLevel;
    }

    /**
     * Configures the global log level.
     * Invalidates all cache.
     *
     * @param level New global level
     */
    public void level(LogLevel level) {
        this.globalLevel = level;
        this.cache.clear();
    }

    /**
     * Returns the current global log level.
     *
     * @return Global level
     */
    public LogLevel level() {
        return globalLevel;
    }

    /**
     * Configures log level for a specific package.
     * Invalidates all cache.
     *
     * @param packageName Full package name (e.g.: "br.framework.mcp")
     * @param level Log level for the package
     */
    public void setPackageLevel(String packageName, LogLevel level) {
        packageFilters.put(packageName, level);
        cache.clear();
    }

    /**
     * Removes log level configuration for a specific package.
     * Invalidates all cache.
     *
     * @param packageName Package name to remove
     */
    public void removePackageLevel(String packageName) {
        packageFilters.remove(packageName);
        cache.clear();
    }

    /**
     * Clears all package configurations.
     * Invalidates all cache.
     */
    public void clearPackageFilters() {
        packageFilters.clear();
        cache.clear();
    }

    /**
     * Checks if package filters are configured.
     *
     * @return true if specific filters exist, false otherwise
     */
    public boolean hasPackageFilters() {
        return !packageFilters.isEmpty();
    }

    /**
     * Returns the configured level for a specific package, or null if not configured.
     *
     * @param packageName Package name
     * @return Configured level or null
     */
    @Nullable
    public LogLevel getPackageLevel(String packageName) {
        return packageFilters.get(packageName);
    }

    /**
     * Returns the effective level for a class, considering package hierarchy.
     * Uses cache to optimize repeated queries.
     *
     * @param className Full class name (e.g.: "br.framework.mcp.MCPServer")
     * @return Effective level (never null)
     */
    public LogLevel getEffectiveLevel(String className) {
        // Try cache first
        val cached = cache.get(className);
        if (cached != null) {
            return cached;
        }

        // Compute effective level
        val effective = computeEffectiveLevel(className);
        cache.put(className, effective);
        return effective;
    }

    /**
     * Computes effective level based on package hierarchy.
     * Searches from most specific to most generic.
     *
     * @param className Full class name
     * @return Effective level
     */
    private LogLevel computeEffectiveLevel(String className) {
        // Search from most specific to most generic
        // For "br.framework.mcp.MCPServer":
        //   1. Try "br.framework.mcp.MCPServer" (exact class)
        //   2. Try "br.framework.mcp" (package)
        //   3. Try "br.framework"
        //   4. Try "br.judia"
        //   5. Use globalLevel

        String current = className;
        while (true) {
            val level = packageFilters.get(current);
            if (level != null) {
                return level;
            }

            // Remove last segment
            val lastDot = current.lastIndexOf('.');
            if (lastDot == -1) {
                break;
            }
            current = current.substring(0, lastDot);
        }

        return globalLevel;
    }
}
