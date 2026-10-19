package br.commons.lang;

import br.commons.lang.xml.XmlReader;
import br.commons.lang.xml.XmlWriter;
import br.commons.serializer.Serializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Factories for the XML format: read text, file or resource to the common representation (read by
 * {@link Serializer}), write a value as text and persist a document. JDK StAX, no third-party databind; DTD and
 * external entities disabled. The root element is the document; attributes and child elements are keys, repeated
 * siblings a list, text beside them the key {@value XmlReader#TEXT}. Values are text (wire policy).
 */
@NullMarked
public final class Xml {

    private Xml() {}

    /** Reads and validates the text; text matching within the cache window returns the same document. */
    public static Serializer from(String text) { return XmlReader.read(text); }

    /** Empty document when the file is missing or unreadable; file matching within the cache window is not re-read. */
    public static Serializer from(@Nullable Path path) { return XmlReader.read(path); }

    /** Empty document when the resource is missing; cached like {@link #from(Path)}. */
    public static Serializer fromClasspath(String resource) { return XmlReader.readClasspath(resource); }

    /**
     * Serializes any value under the root element {@value XmlWriter#ROOT}: primitives, {@code String}, {@code UUID},
     * enums, {@code Map}, {@code List}, {@code record}s and {@link Serializer}s.
     */
    public static String write(@Nullable Object value) { return XmlWriter.write(value, XmlWriter.ROOT); }

    public static String write(@Nullable Object value, String root) { return XmlWriter.write(value, root); }

    /** Writes to the file the document came from, keeping its root name; warning if it did not come from a file. */
    public static void save(Serializer document) { XmlWriter.save(document); }

    public static void save(Serializer document, Path path) { XmlWriter.save(document, path); }
}
