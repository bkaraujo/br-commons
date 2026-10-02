package br.commons.web.server;

import br.commons.http.MediaType;
import br.commons.http.message.HTTPRequest;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Values of an {@code application/x-www-form-urlencoded} body, in UTF-8. Unlike the query string of
 * {@link HTTPRequest#parameters()}, a repeated name keeps every value (checkboxes, multiple
 * selection). Read through {@link WebRequest#body(Class)} on a route that consumes
 * {@link MediaType#URL_ENCODED}.
 */
@NullMarked
public final class Form {

    private final Map<String, List<String>> values;

    private Form(Map<String, List<String>> values) {
        this.values = values;
    }

    /** Parses the encoded text; malformed percent-encoding throws {@link IllegalArgumentException}. */
    public static Form parse(String encoded) {
        val values = new LinkedHashMap<String, List<String>>();
        for (val pair : encoded.split("&")) {
            if (pair.isEmpty()) continue;
            val equals = pair.indexOf('=');
            val name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            val value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            values.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return new Form(values);
    }

    public Optional<String> first(String name) {
        val list = values.get(name);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /** The first value, or an empty text when the field is absent. */
    public String value(String name) {
        return first(name).orElse("");
    }

    public List<String> all(String name) {
        return List.copyOf(values.getOrDefault(name, List.of()));
    }
}
