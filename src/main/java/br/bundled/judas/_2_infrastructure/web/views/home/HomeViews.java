package br.bundled.judas._2_infrastructure.web.views.home;

import br.commons.lang.html.core.Node;
import org.jspecify.annotations.NullMarked;

import static br.bundled.judas._2_infrastructure.web.views.Ui.message;
import static br.commons.lang.Html5.*;

/** The home screen; nothing to show yet, so only its empty state (UI-32). */
@NullMarked
final class HomeViews {

    private HomeViews() {}

    static Node screen() {
        return section(cls("card"), p(cls("empty"), text(message("forms.home.empty"))));
    }
}
