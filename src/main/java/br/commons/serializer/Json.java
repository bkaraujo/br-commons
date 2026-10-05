package br.commons.serializer;

import br.commons.serializer.json.JsonReader;
import br.commons.serializer.json.JsonWriter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Factories for the JSON format: read text to the common representation (read by {@link Serializer}) and write a value
 * as text. Hand-written codec, no third-party databind — wire format is under product control.
 */
@NullMarked
public final class Json {

    private Json() {}

    /**
     * Serializes any value: primitives, {@code String}, {@code UUID}, enums, {@code Map}, {@code List},
     * {@code record}s and {@link Serializer}s.
     */
    public static String write(@Nullable Object value) { return JsonWriter.write(value); }

    /** Reads and validates the text; text matching within the cache window returns the same document. */
    public static Serializer from(String text) { return JsonReader.read(text); }
}
