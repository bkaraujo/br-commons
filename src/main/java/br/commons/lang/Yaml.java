package br.commons.lang;

import br.commons.lang.yaml.YamlReader;
import br.commons.lang.yaml.YamlWriter;
import br.commons.serializer.Serializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factories for the YAML format — typically configuration: read file, resource or map to the common representation
 * (read by {@link Serializer}) and persist a document created or edited by {@link Serializer#builder}.
 */
@NullMarked
public final class Yaml {

    private Yaml() {}

    /** Empty document when the file is missing or unreadable; file matching within the cache window is not re-read. */
    public static Serializer from(@Nullable Path path) { return YamlReader.read(path); }

    /** Empty document when the resource is missing; cached like {@link #from(Path)}. */
    public static Serializer fromClasspath(String resource) { return YamlReader.readClasspath(resource); }

    public static Serializer from(Map<String, Object> map) { return YamlReader.read(map); }

    public static Serializer from() { return from(new LinkedHashMap<>()); }

    /** Writes to the file the document came from; warning if it did not come from a file. */
    public static void save(Serializer document) { YamlWriter.save(document); }

    public static void save(Serializer document, Path path) { YamlWriter.save(document, path); }
}
