package br.commons.lang.yaml;

import br.commons.Logger;
import br.commons.serializer.Serializer;
import br.commons.serializer.document.Document;
import br.commons.serializer.document.DocumentCache;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistence to YAML of any document — read from any format or created by {@link Serializer#builder}.
 * After writing, the file is evicted from the cache of {@link DocumentCache}: the next read sees what was saved.
 */
@NullMarked
public final class YamlWriter {

    private YamlWriter() {}

    public static void save(Serializer document) {
        val source = Document.unwrap(document).source();
        if (source == null) {
            Logger.warn("Failed to save: document not loaded from a known file");
            return;
        }
        save(document, source);
    }

    public static void save(Serializer document, Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            Logger.debug("Writing %s", path);
            YamlReader.MAPPER.writeValue(path.toFile(), Document.unwrap(document).tree());
        } catch (Throwable throwable) {
            Logger.error("Failed to write %s: %s", path, throwable);
        } finally {
            DocumentCache.invalidate(path);
        }
    }
}
