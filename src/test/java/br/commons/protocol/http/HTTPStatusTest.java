package br.commons.protocol.http;

import br.commons.protocol.http.HTTPStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTTPStatusTest {

    @Test
    void customAcceptsOnlyTheProtocolRange() {
        val custom = HTTPStatus.custom(299, "Custom Success", "Fora do registro").getOrThrow();
        assertEquals(299, custom.code());
        assertTrue(HTTPStatus.custom(99, "Too Low", "").isFailure());
        assertTrue(HTTPStatus.custom(600, "Too High", "").isFailure());
    }
}
