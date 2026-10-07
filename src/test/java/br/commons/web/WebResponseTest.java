package br.commons.web;

import br.commons.protocol.http.HTTPStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebResponseTest {

    @Test
    void problemTitleComesFromTheStatusAndTheWireCarriesTheNumber() {
        val response = WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND, "Rota não encontrada"));
        assertEquals(HTTPStatus.NOT_FOUND, response.status());
        assertEquals(WebResponse.JSON, response.header("Content-Type"));
        assertEquals("{\"status\":404,\"title\":\"Not Found\",\"detail\":\"Rota não encontrada\",\"key\":null}",
                new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void eventStreamCarriesTheSseHeaders() {
        val response = WebResponse.eventStream(out -> { });
        assertTrue(response.isStreaming());
        assertEquals(WebResponse.EVENT_STREAM, response.header("content-type"));
        assertEquals("no-cache", response.header("Cache-Control"));
    }
}
