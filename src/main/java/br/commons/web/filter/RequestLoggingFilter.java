package br.commons.web.filter;

import br.commons.Logger;
import br.commons.Strings;
import br.commons.http.message.HTTPRequest;
import br.commons.http.message.HTTPResponse;
import br.commons.http.server.HTTPFilter;
import br.commons.http.server.HTTPHandler;
import lombok.val;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RequestLoggingFilter implements HTTPFilter {

    @Override
    public HTTPResponse apply(HTTPRequest request, HTTPHandler next) {
        val response = next.handle(request);
        if (!Strings.containsAny(request.path(), "static", ".well-known", "webmanifest"))
            Logger.info("%s %s -> %d %s", request.method(), request.path(), response.status().code(), response.status().name());

        return response;
    }
}
