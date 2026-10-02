package br.commons.web.server;

import br.commons.Result;
import br.commons.http.MediaType;
import br.commons.http.Multipart;
import br.commons.http.message.HTTPMethod;
import br.commons.http.message.HTTPRequest;
import br.commons.serializer.Json;
import br.commons.web.router.Route;
import br.commons.web.router.Router;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The {@link HTTPRequest} that reached a route: adds to the raw request the path params that the
 * {@link Router} extracted and the reading of the body in the format that the {@link Route}
 * declared in {@code consumes} — not in the client's {@code Content-Type}. Everything else is delegated:
 * attributes written by filters remain visible.
 */
@NullMarked
public final class WebRequest implements HTTPRequest {

    private final HTTPRequest request;
    private final Route route;
    private final Map<String, String> pathParams;

    public WebRequest(HTTPRequest request, Route route, Map<String, String> pathParams) {
        this.request = request;
        this.route = route;
        this.pathParams = Map.copyOf(pathParams);
    }

    public Route route() { return route; }


    @CheckReturnValue
    public Result<String, String> pathParam(String name) {
        val value = pathParams.get(name);
        if (value == null) return Result.failure("No path param '" + name + "'");
        return Result.success(value);
    }

    /** Reads the body as {@code type} in the {@link MediaType} registered in the route. */
    @CheckReturnValue
    public <T> Result<T, String> body(Class<T> type) {
        val consumes = route.consumes();
        if (consumes == null) {
            return Result.failure("Route " + route.method() + " " + route.template() + " declares no request body");
        }
        return switch (consumes) {
            case APPLICATION_JSON -> json(type);
            case TEXT_PLAIN -> text(type);
            case URL_ENCODED -> form(type);
            case MULTIPART_FORM_DATA -> type == Multipart.class
                    ? Result.success(type.cast(request.multipart()))
                    : Result.failure("MULTIPART_FORM_DATA body reads only as Multipart, not " + type.getSimpleName());
            default -> Result.failure("No reader for " + consumes + " body on " + route.method() + " " + route.template());
        };
    }

    private <T> Result<T, String> json(Class<T> type) {
        try {
            return Result.success(Json.from(new String(body(), StandardCharsets.UTF_8)).as(type));
        } catch (RuntimeException e) {
            return Result.failure("Invalid " + type.getSimpleName() + " body: " + e.getMessage());
        }
    }

    private <T> Result<T, String> form(Class<T> type) {
        if (type != Form.class) {
            return Result.failure("URL_ENCODED body reads only as Form, not " + type.getSimpleName());
        }
        try {
            return Result.success(type.cast(Form.parse(new String(body(), StandardCharsets.UTF_8))));
        } catch (IllegalArgumentException e) {
            return Result.failure("Malformed form body: " + e.getMessage());
        }
    }

    private <T> Result<T, String> text(Class<T> type) {
        if (type != String.class) {
            return Result.failure("TEXT_PLAIN body reads only as String, not " + type.getSimpleName());
        }
        return Result.success(type.cast(new String(body(), StandardCharsets.UTF_8)));
    }

    @Override
    public String requestId() { return request.requestId(); }

    @Override
    public HTTPMethod method() { return request.method(); }

    @Override
    public String path() { return request.path(); }

    @Override
    public Map<String, String> parameters() { return request.parameters(); }

    @Override
    public Map<String, String> headers() { return request.headers(); }

    @Override
    public byte[] body() { return request.body(); }

    @Override
    public Multipart multipart() { return request.multipart(); }

    @Override
    public Map<String, Object> attributes() { return request.attributes(); }
}
