package br.bundled.judas._2_infrastructure.web.views.home;

import br.bundled.judas._2_infrastructure.web.views.Layout;
import br.bundled.judas._2_infrastructure.web.views.Screen;
import br.commons.Result;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Route;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/** {@code GET /}: the home screen. */
@NullMarked
public final class HomeRoutes implements WebResource {

    @Override
    public List<Result<Route, String>> routes() {
        return List.of(Route.get(Screen.HOME.path(), MediaType.TEXT_HTML, String.class, false, this::home));
    }

    private HTTPResponse home(WebRequest request) {
        return Layout.page(request, Screen.HOME, HomeViews.screen());
    }
}
