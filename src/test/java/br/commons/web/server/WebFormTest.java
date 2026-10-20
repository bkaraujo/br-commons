package br.commons.web.server;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPMethod;
import br.commons.protocol.http.server.message.HTTPServerRequest;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Additions to the POC: url-encoded forms, HTML responses and post/redirect/get. */
class WebFormTest {

    private WebRequest post(String body) {
        val router = new Router();
        val route = router.post("/login", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class,
                request -> WebResponse.noContent()).getOrThrow();
        val raw = new HTTPServerRequest("", HTTPMethod.POST, "/login", Map.of(), body.getBytes(StandardCharsets.UTF_8));
        return new WebRequest(raw, route, Map.of());
    }

    @Test
    void readsUrlEncodedFormsWithRepeatedFields() {
        val form = post("cpf=123.456&tag=a&tag=b%20c&flag&=ignored&").body(Form.class).getOrThrow();
        assertEquals("123.456", form.value("cpf"));
        assertEquals(List.of("a", "b c"), form.all("tag"));
        assertEquals("", form.value("flag"));
        assertEquals("", form.value("absent"));
        assertEquals(Optional.empty(), form.first("absent"));
        assertEquals(List.of(), form.all("absent"));
    }

    @Test
    void formBodyReadsOnlyAsFormAndMustBeWellEncoded() {
        assertTrue(post("a=1").body(String.class) instanceof Result.Failure<String, String>(var error)
                && error.equals(Errors.get().urlEncodedOnlyAsForm("String").value()));
        assertTrue(post("a=%zz").body(Form.class) instanceof Result.Failure<Form, String>(var error)
                && error.startsWith(Errors.get().malformedForm("").value()));
    }

    @Test
    void htmlAndRedirectResponses() {
        val page = WebResponse.html(HTTPStatus.OK, "<p>olá</p>");
        assertEquals("text/html; charset=utf-8", page.header("Content-Type"));
        assertEquals("<p>olá</p>", new String(page.body(), StandardCharsets.UTF_8));
        val redirect = WebResponse.redirect("/");
        assertEquals(303, redirect.status().code());
        assertEquals("/", redirect.header("Location"));
    }
}
