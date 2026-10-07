package br.commons.lang.json;

import br.commons.serializer.document.Document;
import br.commons.serializer.document.DocumentCache;
import br.commons.serializer.document.Policy;
import org.jspecify.annotations.NullMarked;

/**
 * JSON reading: {@link JsonParser} validates syntax ({@link IllegalArgumentException} with position) and the
 * tree becomes the common representation, {@link Document}, with wire data policy ({@link Policy#WIRE}).
 * Text matching within the cache window returns the same document.
 */
@NullMarked
public final class JsonReader {

    private JsonReader() {}

    public static Document read(String text) {
        return DocumentCache.cached(text, () -> Document.of(new JsonParser(text).parseDocument(), null, Policy.WIRE));
    }
}
