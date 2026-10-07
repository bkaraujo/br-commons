package br.commons.protocol.http;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * HTTP response status. The framework only carries this object — the number appears only at the edge
 * ({@code sendResponseHeaders}, error JSON body). Constants cover the IANA registry with RFC 9110 names;
 * a code outside it goes through {@link #custom}, the only way out of this class — the constructor is
 * private so no status is created without passing range validation.
 */
@NullMarked
public final class HTTPStatus {

    private final int code;
    private final String name;
    /** {@code null} for the registry constants: their description comes from the i18n bundle ({@link Errors#httpStatus}). */
    private final @Nullable String description;

    private HTTPStatus(int code, String name, @Nullable String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    // 1xx — informational
    public static final HTTPStatus CONTINUE = new HTTPStatus(100, "Continue", null);
    public static final HTTPStatus SWITCHING_PROTOCOLS = new HTTPStatus(101, "Switching Protocols", null);
    public static final HTTPStatus PROCESSING = new HTTPStatus(102, "Processing", null);
    public static final HTTPStatus EARLY_HINTS = new HTTPStatus(103, "Early Hints", null);

    // 2xx — success
    public static final HTTPStatus OK = new HTTPStatus(200, "OK", null);
    public static final HTTPStatus CREATED = new HTTPStatus(201, "Created", null);
    public static final HTTPStatus ACCEPTED = new HTTPStatus(202, "Accepted", null);
    public static final HTTPStatus NON_AUTHORITATIVE_INFORMATION = new HTTPStatus(203, "Non-Authoritative Information", null);
    public static final HTTPStatus NO_CONTENT = new HTTPStatus(204, "No Content", null);
    public static final HTTPStatus RESET_CONTENT = new HTTPStatus(205, "Reset Content", null);
    public static final HTTPStatus PARTIAL_CONTENT = new HTTPStatus(206, "Partial Content", null);
    public static final HTTPStatus MULTI_STATUS = new HTTPStatus(207, "Multi-Status", null);
    public static final HTTPStatus ALREADY_REPORTED = new HTTPStatus(208, "Already Reported", null);
    public static final HTTPStatus IM_USED = new HTTPStatus(226, "IM Used", null);

    // 3xx — redirects
    public static final HTTPStatus MULTIPLE_CHOICES = new HTTPStatus(300, "Multiple Choices", null);
    public static final HTTPStatus MOVED_PERMANENTLY = new HTTPStatus(301, "Moved Permanently", null);
    public static final HTTPStatus FOUND = new HTTPStatus(302, "Found", null);
    public static final HTTPStatus SEE_OTHER = new HTTPStatus(303, "See Other", null);
    public static final HTTPStatus NOT_MODIFIED = new HTTPStatus(304, "Not Modified", null);
    public static final HTTPStatus USE_PROXY = new HTTPStatus(305, "Use Proxy", null);
    public static final HTTPStatus TEMPORARY_REDIRECT = new HTTPStatus(307, "Temporary Redirect", null);
    public static final HTTPStatus PERMANENT_REDIRECT = new HTTPStatus(308, "Permanent Redirect", null);

    // 4xx — client errors
    public static final HTTPStatus BAD_REQUEST = new HTTPStatus(400, "Bad Request", null);
    public static final HTTPStatus UNAUTHORIZED = new HTTPStatus(401, "Unauthorized", null);
    public static final HTTPStatus PAYMENT_REQUIRED = new HTTPStatus(402, "Payment Required", null);
    public static final HTTPStatus FORBIDDEN = new HTTPStatus(403, "Forbidden", null);
    public static final HTTPStatus NOT_FOUND = new HTTPStatus(404, "Not Found", null);
    public static final HTTPStatus METHOD_NOT_ALLOWED = new HTTPStatus(405, "Method Not Allowed", null);
    public static final HTTPStatus NOT_ACCEPTABLE = new HTTPStatus(406, "Not Acceptable", null);
    public static final HTTPStatus PROXY_AUTHENTICATION_REQUIRED = new HTTPStatus(407, "Proxy Authentication Required", null);
    public static final HTTPStatus REQUEST_TIMEOUT = new HTTPStatus(408, "Request Timeout", null);
    public static final HTTPStatus CONFLICT = new HTTPStatus(409, "Conflict", null);
    public static final HTTPStatus GONE = new HTTPStatus(410, "Gone", null);
    public static final HTTPStatus LENGTH_REQUIRED = new HTTPStatus(411, "Length Required", null);
    public static final HTTPStatus PRECONDITION_FAILED = new HTTPStatus(412, "Precondition Failed", null);
    public static final HTTPStatus CONTENT_TOO_LARGE = new HTTPStatus(413, "Content Too Large", null);
    public static final HTTPStatus URI_TOO_LONG = new HTTPStatus(414, "URI Too Long", null);
    public static final HTTPStatus UNSUPPORTED_MEDIA_TYPE = new HTTPStatus(415, "Unsupported Media Type", null);
    public static final HTTPStatus RANGE_NOT_SATISFIABLE = new HTTPStatus(416, "Range Not Satisfiable", null);
    public static final HTTPStatus EXPECTATION_FAILED = new HTTPStatus(417, "Expectation Failed", null);
    public static final HTTPStatus IM_A_TEAPOT = new HTTPStatus(418, "I'm a teapot", null);
    public static final HTTPStatus MISDIRECTED_REQUEST = new HTTPStatus(421, "Misdirected Request", null);
    public static final HTTPStatus UNPROCESSABLE_CONTENT = new HTTPStatus(422, "Unprocessable Content", null);
    public static final HTTPStatus LOCKED = new HTTPStatus(423, "Locked", null);
    public static final HTTPStatus FAILED_DEPENDENCY = new HTTPStatus(424, "Failed Dependency", null);
    public static final HTTPStatus TOO_EARLY = new HTTPStatus(425, "Too Early", null);
    public static final HTTPStatus UPGRADE_REQUIRED = new HTTPStatus(426, "Upgrade Required", null);
    public static final HTTPStatus PRECONDITION_REQUIRED = new HTTPStatus(428, "Precondition Required", null);
    public static final HTTPStatus TOO_MANY_REQUESTS = new HTTPStatus(429, "Too Many Requests", null);
    public static final HTTPStatus REQUEST_HEADER_FIELDS_TOO_LARGE = new HTTPStatus(431, "Request Header Fields Too Large", null);
    public static final HTTPStatus UNAVAILABLE_FOR_LEGAL_REASONS = new HTTPStatus(451, "Unavailable For Legal Reasons", null);

    // 5xx — server errors
    public static final HTTPStatus INTERNAL_SERVER_ERROR = new HTTPStatus(500, "Internal Server Error", null);
    public static final HTTPStatus NOT_IMPLEMENTED = new HTTPStatus(501, "Not Implemented", null);
    public static final HTTPStatus BAD_GATEWAY = new HTTPStatus(502, "Bad Gateway", null);
    public static final HTTPStatus SERVICE_UNAVAILABLE = new HTTPStatus(503, "Service Unavailable", null);
    public static final HTTPStatus GATEWAY_TIMEOUT = new HTTPStatus(504, "Gateway Timeout", null);
    public static final HTTPStatus HTTP_VERSION_NOT_SUPPORTED = new HTTPStatus(505, "HTTP Version Not Supported", null);
    public static final HTTPStatus VARIANT_ALSO_NEGOTIATES = new HTTPStatus(506, "Variant Also Negotiates", null);
    public static final HTTPStatus INSUFFICIENT_STORAGE = new HTTPStatus(507, "Insufficient Storage", null);
    public static final HTTPStatus LOOP_DETECTED = new HTTPStatus(508, "Loop Detected", null);
    public static final HTTPStatus NOT_EXTENDED = new HTTPStatus(510, "Not Extended", null);
    public static final HTTPStatus NETWORK_AUTHENTICATION_REQUIRED = new HTTPStatus(511, "Network Authentication Required", null);

    /** Status outside the registry — the code must be in the range that the protocol accepts (100–599). */
    @CheckReturnValue
    public static Result<HTTPStatus, String> custom(int code, String name, String description) {
        if (code < 100 || code > 599) {
            return Result.failure(Errors.get().httpStatusOutOfRange(String.valueOf(code)).value());
        }
        return Result.success(new HTTPStatus(code, name, description));
    }

    public int code() { return code; }
    public String name() { return name; }
    /** In the {@code br.commons.locale} locale for registry constants; as given for {@link #custom}. */
    public String description() { return description != null ? description : Errors.httpStatus(code); }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof HTTPStatus status
                && code == status.code
                && name.equals(status.name)
                && Objects.equals(description, status.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, description);
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
