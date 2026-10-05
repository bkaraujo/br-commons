package br.commons.serializer.document.builder;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/** A key/value pair for {@link DocumentBuilder}: becomes a map entry in the document. */
@NullMarked
public record Entry(String key, Object value) {

    public Map<String, Object> toMap() {
        val map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }
}
