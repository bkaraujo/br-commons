package br.commons.examples.tracker.jquery.api;

import br.commons.Result;
import br.commons.examples.tracker.shared.Ids;
import br.commons.html.form.Violation;
import br.commons.http.HTTPStatus;
import br.commons.http.message.HTTPResponse;
import br.commons.web.ProblemDetail;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** What every route of the API shares: the JSON body read by field name, the id of the path and the error answers. */
@NullMarked
final class Json {

    private Json() {}

    /** A field the server refused, with the message to show next to it. */
    record FieldError(String field, String message) {}

    /** Body of a 422: {@code {"errors": [{"field": "name", "message": "…"}]}}. */
    record Rejection(List<FieldError> errors) {}

    /**
     * Answers with {@code handler}, given the JSON object of the body read by field name as the {@code *Fields}
     * expect ({@code ""} when absent); a body that is not an object answers 400.
     */
    static HTTPResponse withValues(WebRequest request, Function<Function<String, String>, HTTPResponse> handler) {
        return switch (request.body(Map.class)) {
            case Result.Success<Map, String>(var body) when body != null -> handler.apply(name -> text(body.get(name)));
            case Result.Success<Map, String> ignored -> badRequest("Corpo vazio");
            case Result.Failure<Map, String>(var error) -> badRequest(error);
        };
    }

    private static String text(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** The {@code {id}} of the route; {@code -1} (matches nothing, answers 404) when it is not a number. */
    static int id(WebRequest request) {
        val id = Ids.number(request.pathParam("id").getOrElse(""));
        return id == null ? -1 : id;
    }

    static HTTPResponse rejected(List<Violation> violations, Function<Violation, String> messages) {
        return WebResponse.json(HTTPStatus.UNPROCESSABLE_CONTENT, new Rejection(violations.stream()
                .map(violation -> new FieldError(violation.field(), messages.apply(violation)))
                .toList()));
    }

    static HTTPResponse notFound() {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND, "Não encontrado"));
    }

    static HTTPResponse conflict(String detail) {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.CONFLICT, detail));
    }

    static HTTPResponse badRequest(String detail) {
        return WebResponse.problem(ProblemDetail.of(HTTPStatus.BAD_REQUEST, detail));
    }
}
