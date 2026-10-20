package br.bundled.tracker.htmx.ui;

import br.commons.lang.html.core.Node;
import org.jspecify.annotations.NullMarked;

import static br.bundled.tracker.htmx.ui.Ui.message;
import static br.commons.lang.Html5.*;

/** The badge of an active or inactive register item. */
@NullMarked
public final class Status {

    private Status() {}

    public static Node of(boolean active) {
        return span(cls(active ? "badge active" : "badge inactive"), text(message(active ? "Ativo" : "Inativo")));
    }
}
