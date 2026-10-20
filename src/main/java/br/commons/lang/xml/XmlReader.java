package br.commons.lang.xml;

import br.commons.Logger;
import br.commons.locale.i18n.Errors;
import br.commons.serializer.document.Document;
import br.commons.serializer.document.DocumentCache;
import br.commons.serializer.document.Policy;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML reading: StAX (JDK) handles syntax — DTD and external entities disabled (XXE) — and the tree becomes the
 * common representation, {@link Document}, with wire data policy ({@link Policy#WIRE}). Mapping: the root element is
 * the document; attributes and child elements are keys; repeated siblings become a list; text beside attributes or
 * children is the key {@value #TEXT}; empty element is {@code null}. Values are always text. Text, files and
 * resources go through the cache of {@link DocumentCache}; missing file or resource is an empty document, file that
 * fails to read logs an error and returns an empty document.
 */
@NullMarked
public final class XmlReader {

    /** Key of the text of an element that also has attributes or children. */
    public static final String TEXT = "value";

    private static final String CLASSPATH = "classpath:";

    private static final String REASON = "Message: ";

    private static final XMLInputFactory FACTORY = factory();

    private XmlReader() {}

    public static Document read(String text) {
        return DocumentCache.cached(text, () -> Document.of(parse(new StringReader(text)), null, Policy.WIRE));
    }

    public static Document read(@Nullable Path path) {
        if (path == null) return Document.empty(null, Policy.WIRE);
        return DocumentCache.cached(path, () -> load(path));
    }

    public static Document readClasspath(String resource) {
        return DocumentCache.cached(CLASSPATH + resource, () -> loadClasspath(resource));
    }

    /** Name of the root element of the file, or {@code null} if missing or unreadable — kept when rewriting. */
    static @Nullable String rootName(Path path) {
        if (!Files.exists(path)) return null;
        try (Reader input = Files.newBufferedReader(path)) {
            val reader = FACTORY.createXMLStreamReader(input);
            try {
                return toRoot(reader) ? reader.getLocalName() : null;
            } finally {
                reader.close();
            }
        } catch (IOException | XMLStreamException | RuntimeException e) {
            return null;
        }
    }

    private static Document load(Path path) {
        try {
            if (!Files.exists(path) || Files.size(path) == 0) return Document.empty(path, Policy.WIRE);
            try (Reader input = Files.newBufferedReader(path)) {
                return Document.of(parse(input), path, Policy.WIRE);
            }
        } catch (Throwable throwable) {
            Logger.error("Failed to read %s: %s", path, throwable);
            return Document.empty(path, Policy.WIRE);
        }
    }

    private static Document loadClasspath(String resource) {
        try (InputStream input = XmlReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return Document.empty(null, Policy.WIRE);
            return Document.of(parse(input), null, Policy.WIRE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static @Nullable Object parse(Reader input) {
        try {
            return parse(FACTORY.createXMLStreamReader(input));
        } catch (XMLStreamException e) {
            throw malformed(e);
        }
    }

    private static @Nullable Object parse(InputStream input) {
        try {
            return parse(FACTORY.createXMLStreamReader(input));
        } catch (XMLStreamException e) {
            throw malformed(e);
        }
    }

    /** The root element and nothing but comments or whitespace after it (StAX checks the rest of the document). */
    private static @Nullable Object parse(XMLStreamReader reader) throws XMLStreamException {
        try {
            if (!toRoot(reader)) return null;
            val root = element(reader);
            while (reader.hasNext()) reader.next();
            return root;
        } finally {
            reader.close();
        }
    }

    /** Skips the prolog (declaration, comments, DOCTYPE); {@code false} when there is no root element. */
    private static boolean toRoot(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) return true;
        }
        return false;
    }

    /** Reads from the start tag to the matching end tag. */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    private static @Nullable Object element(XMLStreamReader reader) throws XMLStreamException {
        val map = new LinkedHashMap<String, @Nullable Object>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            add(map, reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
        val text = new StringBuilder();
        while (true) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> add(map, reader.getLocalName(), element(reader));
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> text.append(reader.getText());
                case XMLStreamConstants.END_ELEMENT -> { return value(map, text.toString()); }
                default -> { /* comments, processing instructions */ }
            }
        }
    }

    private static @Nullable Object value(Map<String, @Nullable Object> map, String text) {
        if (map.isEmpty()) return text.isEmpty() ? null : text;
        // Whitespace between children is layout, not content.
        if (!text.isBlank()) add(map, TEXT, text.strip());
        return map;
    }

    /** Repeated key becomes a list, in document order; an element value is never a list itself. */
    @SuppressWarnings("unchecked")
    private static void add(Map<String, @Nullable Object> map, String key, @Nullable Object value) {
        if (!map.containsKey(key)) {
            map.put(key, value);
            return;
        }
        if (map.get(key) instanceof List<?> list) {
            ((List<@Nullable Object>) list).add(value);
            return;
        }
        val list = new ArrayList<@Nullable Object>();
        list.add(map.get(key));
        list.add(value);
        map.put(key, list);
    }

    private static IllegalArgumentException malformed(XMLStreamException e) {
        val location = e.getLocation();
        val line = location != null ? String.valueOf(location.getLineNumber()) : "?";
        val column = location != null ? String.valueOf(location.getColumnNumber()) : "?";
        // The JDK message repeats the position ("ParseError at [row,col]:[1,9]\nMessage: ..."); keep only the reason.
        val message = String.valueOf(e.getMessage());
        val at = message.indexOf(REASON);
        val reason = at < 0 ? message : message.substring(at + REASON.length());
        return new IllegalArgumentException(Errors.get().xmlMalformed(line, column, reason).value(), e);
    }

    private static XMLInputFactory factory() {
        val factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }
}
