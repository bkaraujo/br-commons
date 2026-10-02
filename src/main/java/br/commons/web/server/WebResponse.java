package br.commons.web.server;

import br.commons.http.HTTPStatus;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.message.HTTPServerResponse;
import br.commons.serializer.Json;
import br.commons.web.ProblemDetail;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Factories of {@link HTTPResponse} in web format: JSON body in UTF-8, error {@link ProblemDetail} and SSE. */
@NullMarked
public final class WebResponse {

    public static final String JSON = MediaType.APPLICATION_JSON.contentType();
    public static final String EVENT_STREAM = MediaType.TEXT_EVENT_STREAM.contentType();

    /** Error form on the wire: numeric {@code status}, which is what the HTTP client expects to read. */
    @NullMarked
    private record ProblemBody(int status, String title, String detail, @Nullable String key) {}

    private WebResponse() {}

    public static HTTPResponse json(HTTPStatus status, Object body) {
        return HTTPServerResponse.status(status, Json.write(body).getBytes(StandardCharsets.UTF_8)).mediaType(MediaType.APPLICATION_JSON);
    }

    public static HTTPResponse ok(Object body) {
        return json(HTTPStatus.OK, body);
    }

    public static HTTPResponse created(Object body) {
        return json(HTTPStatus.CREATED, body);
    }

    /** Server-rendered page or HTMX fragment, UTF-8. */
    public static HTTPResponse html(HTTPStatus status, String html) {
        return HTTPServerResponse.status(status, html.getBytes(StandardCharsets.UTF_8)).mediaType(MediaType.TEXT_HTML);
    }

    /** 303 See Other: after a form POST the browser follows with a GET (post/redirect/get). */
    public static HTTPResponse redirect(String location) {
        return HTTPServerResponse.status(HTTPStatus.SEE_OTHER).header("Location", location);
    }

    public static HTTPResponse noContent() {
        return HTTPServerResponse.noContent();
    }

    public static HTTPResponse problem(ProblemDetail problem) {
        return json(problem.status(), new ProblemBody(problem.status().code(), problem.title(), problem.detail(), problem.key()));
    }

    /** With {@link ProblemDetail} body: the HTTP client expects a readable problem in every error, 500 included. */
    public static HTTPResponse internalServerError() {
        return problem(ProblemDetail.of(HTTPStatus.INTERNAL_SERVER_ERROR));
    }

    /** {@link HTTPServerResponse#stream} with Server-Sent Events headers. */
    public static HTTPResponse eventStream(Consumer<OutputStream> writer) {
        return HTTPServerResponse.stream(writer)
                .mediaType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive");
    }
}
