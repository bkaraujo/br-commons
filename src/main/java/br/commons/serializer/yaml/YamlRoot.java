package br.commons.serializer.yaml;

import br.commons.Logger;
import br.commons.Tuple;
import br.commons.serializer.Yaml;
import br.commons.serializer.core.TreeSerializer;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yaml aggregator that delegates to specialized reader and writer classes. The scalar accessors, {@code asEnum}
 * and the binding come from {@link TreeSerializer}, over the reader's lookup ({@link #find}) and typed, cached
 * values ({@link #value}).
 * <p>
 * Reading operations use a FlatMap for O(1) access and an LRU cache.
 * Writing operations use the standard HashMap structure.
 */
@NullMarked
public final class YamlRoot extends TreeSerializer implements Yaml {
    private static final ObjectMapper MAPPER = YAMLMapper.builder().build();

    private final YamlReader reader;
    private final YamlWriter writer;

    // ========== Factory Methods ==========

    private static YamlRoot empty() {
        return from(new LinkedHashMap<>());
    }

    public static YamlRoot from(@Nullable Path path) {
        if (path == null || !Files.exists(path)) {
            return empty();
        }

        try {
            if (Files.size(path) == 0) {
                val yaml = empty();
                yaml.reader.setSource(path);
                yaml.writer.setSource(path);
                return yaml;
            }

            val raw = MAPPER.readValue(path.toFile(), Map.class);
            val yaml = from(raw);
            yaml.reader.setSource(path);
            yaml.writer.setSource(path);
            return yaml;
        } catch (Throwable throwable) {
            Logger.error("Failed to read %s: %s", path, throwable);
            return empty();
        }
    }

    /** Reads a YAML resource from the classpath; empty when the resource does not exist. */
    @SuppressWarnings("unchecked")
    public static YamlRoot fromClasspath(String resource) {
        try (val input = YamlRoot.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return empty();
            }
            return from((Map<String, Object>) MAPPER.readValue(input, Map.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static YamlRoot from(Map<String, Object> values) {
        return new YamlRoot(values);
    }

    private YamlRoot(Map<String, Object> raw) {
        this.reader = new YamlReader(raw);
        this.writer = new YamlWriter(raw, reader);
    }

    YamlReader reader() { return reader; }
    YamlWriter writer() { return writer; }

    // ========== Reading: the tree, with the cache and policy of the reader ==========

    @Override
    protected Object tree() {
        return reader.getRaw();
    }

    @Override
    protected @Nullable Object find(String key) {
        return reader.find(key);
    }

    /** Cached by the reader, text trimmed, a number that does not parse is a fatal configuration error. */
    @Override
    protected <T> @Nullable T value(String key, Class<T> type) {
        return reader.typed(key, type);
    }

    /** A key whose value is {@code null} in the file still exists. */
    @Override
    public boolean contains(String key) {
        return reader.contains(key);
    }

    @Override
    @Nullable
    public Path folder() {
        return reader.folder();
    }

    @Override public byte[]   asBytes   (String key) { return reader.asBytes(key); }
    @Override public short[]  asShorts  (String key) { return reader.asShorts(key); }
    @Override public int[]    asInts    (String key) { return reader.asInts(key); }
    @Override public long[]   asLongs   (String key) { return reader.asLongs(key); }
    @Override public float[]  asFloats  (String key) { return reader.asFloats(key); }
    @Override public double[] asDoubles (String key) { return reader.asDoubles(key); }

    @Override
    public Tuple[] asTuples(String key) {
        return reader.asTuples(key);
    }

    @Override
    public List<Yaml> list(String key) {
        return reader.list(key);
    }

    @Override
    @Nullable
    public Yaml subtree(String key) {
        return reader.subtree(key);
    }

    // ========== Delegated Write Operations ==========

    @Override
    public void clear() {
        writer.clear();
    }

    @Override
    public void append(String key, String value) {
        writer.append(key, value);
    }

    @Override
    public void append(YamlEntry... values) {
        writer.append(values);
    }

    @Override
    public void append(String key, String name, Object value) {
        writer.append(key, name, value);
    }

    @Override
    public void append(String key, YamlEntry... values) {
        writer.append(key, values);
    }

    @Override
    public void put(String key, Object value) {
        writer.put(key, value);
    }

    @Override
    public void save() {
        writer.save();
    }

    @Override
    public void save(Path path) {
        writer.save(path);
    }

    // ========== Object Methods ==========

    @Override
    public String toString() {
        val source = reader.source();
        return source == null ? "null" : source.toString();
    }
}
