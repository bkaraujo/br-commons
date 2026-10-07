package br.commons.web;

import br.commons.protocol.http.HTTPStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Uniform error body — simplified RFC 7807. Never carries CPF (ArchUnit rule from Phase 7).
 * {@code title} comes from {@link HTTPStatus#name()}; the JSON that goes out to the client carries
 * the numeric code in {@code status}, built by {@code HTTPResponse.problem}.
 */
@NullMarked
public record ProblemDetail(HTTPStatus status, String title, String detail, @Nullable String key) {

    public static ProblemDetail of(HTTPStatus status) {
        return new ProblemDetail(status, status.name(), status.description(), null);
    }

    public static ProblemDetail of(HTTPStatus status, String detail) {
        return new ProblemDetail(status, status.name(), detail, null);
    }

    public static ProblemDetail of(HTTPStatus status, String detail, String key) {
        return new ProblemDetail(status, status.name(), detail, key);
    }
}
