package br.commons.http;

import br.commons.Strings;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Format of the body of an HTTP message. {@link #value()} is the MIME name, as it appears on the wire and in
 * OpenAPI; {@link #contentType()} is the complete header value.
 */
@NullMarked
public enum MediaType {
    APPLICATION_JSON("application/json"),
    APPLICATION_XML("application/xml"),

    TEXT_HTML("text/html"),
    TEXT_PLAIN("text/plain"),
    TEXT_XML("text/xml"),
    TEXT_EVENT_STREAM("text/event-stream"),
    TEXT_JAVASCRIPT("text/javascript"),

    URL_ENCODED("application/x-www-form-urlencoded"),

    MULTIPART_FORM_DATA("multipart/form-data"),
    MULTIPART_MIXED("multipart/mixed");

    private final String value;

    MediaType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Value of {@code Content-Type}: the MIME name with {@code charset=utf-8}. Multipart comes out
     * without a parameter — the {@code boundary} belongs to whoever builds the body.
     */
    public String contentType() {
        return this == MULTIPART_FORM_DATA || this == MULTIPART_MIXED ? value : value + "; charset=utf-8";
    }

    /**
     * The type named by a {@code Content-Type}, ignoring parameters ({@code charset},
     * {@code boundary}) and case; {@code null} if the header is missing or the type is not here.
     */
    public static @Nullable MediaType fromContentType(@Nullable String contentType) {
        if (contentType == null) return null;

        val semicolon = contentType.indexOf(';');
        val name = Strings.lower((semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim());
        for (val type : values()) {
            if (type.value.equals(name)) return type;
        }

        return null;
    }
}
