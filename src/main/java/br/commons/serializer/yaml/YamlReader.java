package br.commons.serializer.yaml;

import br.commons.Logger;
import br.commons.serializer.document.Document;
import br.commons.serializer.document.DocumentCache;
import br.commons.serializer.document.Policy;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * YAML reading: Jackson handles syntax, this validates structure — the root of a configuration document must be a
 * map — and the tree becomes the common representation, {@link Document}, with configuration policy
 * ({@link Policy#CONFIG}). Missing file or resource is an empty document; file that fails to read logs an error and
 * returns an empty document. Files and resources go through the cache of {@link DocumentCache}.
 */
@NullMarked
public final class YamlReader {

    static final ObjectMapper MAPPER = YAMLMapper.builder().build();

    private static final String CLASSPATH = "classpath:";

    private YamlReader() {}

    public static Document read(@Nullable Path path) {
        if (path == null) return Document.empty(null, Policy.CONFIG);
        return DocumentCache.cached(path, () -> load(path));
    }

    public static Document readClasspath(String resource) {
        return DocumentCache.cached(CLASSPATH + resource, () -> loadClasspath(resource));
    }

    public static Document read(Map<String, Object> map) {
        return Document.of(map, null, Policy.CONFIG);
    }

    private static Document load(Path path) {
        try {
            if (!Files.exists(path) || Files.size(path) == 0) return Document.empty(path, Policy.CONFIG);
            return validated(MAPPER.readValue(path.toFile(), Object.class), path);
        } catch (Throwable throwable) {
            Logger.error("Failed to read %s: %s", path, throwable);
            return Document.empty(path, Policy.CONFIG);
        }
    }

    private static Document loadClasspath(String resource) {
        try (InputStream input = YamlReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return Document.empty(null, Policy.CONFIG);
            return validated(MAPPER.readValue(input, Object.class), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Empty document ({@code null}) or map; any other root is not configuration. */
    private static Document validated(@Nullable Object tree, @Nullable Path source) {
        if (tree == null) return Document.empty(source, Policy.CONFIG);
        if (!(tree instanceof Map<?, ?>)) {
            val shown = source != null ? source.toString() : "resource";
            throw new IllegalArgumentException("YAML root must be a map in " + shown + ", got " + tree.getClass().getSimpleName());
        }
        return Document.of(tree, source, Policy.CONFIG);
    }
}
