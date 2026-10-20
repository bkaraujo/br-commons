package br.bundled.money.web;

import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.module.Lifecycle;
import br.commons.web.filter.RequestLoggingFilter;
import br.commons.web.server.WebServer;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class WebModule implements Lifecycle {
    
    private @Nullable WebServer server;

    public Result<Void, BusinessError> initialize() {
        server = new WebServer(9000, List.of(
                new RequestLoggingFilter()
        ));
        return server.initialize();
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        if (server != null) {
            return server.terminate();
        }

        return Result.success();
    }
}
