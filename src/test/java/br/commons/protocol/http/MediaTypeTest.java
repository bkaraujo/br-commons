package br.commons.protocol.http;

import br.commons.protocol.http.MediaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MediaTypeTest {

    @Test
    void contentTypeIgnoresParametersAndCase() {
        assertEquals(MediaType.APPLICATION_JSON, MediaType.fromContentType("Application/JSON; charset=utf-8"));
        assertEquals(MediaType.MULTIPART_FORM_DATA, MediaType.fromContentType("multipart/form-data; boundary=x"));
        assertEquals(MediaType.TEXT_PLAIN, MediaType.fromContentType(" text/plain "));
    }

    @Test
    void absentOrUnknownContentTypeIsNull() {
        assertNull(MediaType.fromContentType(null));
        assertNull(MediaType.fromContentType("application/pdf"));
    }

    @Test
    void headerValueCarriesCharsetExceptForMultipart() {
        assertEquals("text/plain; charset=utf-8", MediaType.TEXT_PLAIN.contentType());
        assertEquals("multipart/mixed", MediaType.MULTIPART_MIXED.contentType());
    }
}
