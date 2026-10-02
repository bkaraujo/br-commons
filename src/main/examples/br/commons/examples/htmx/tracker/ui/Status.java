package br.commons.examples.htmx.tracker.ui;

import br.commons.html.core.Node;
import org.jspecify.annotations.NullMarked;

import static br.commons.examples.htmx.tracker.ui.Ui.message;
import static br.commons.html.html5.Html5.*;

/** The badge of an active or inactive register item. */
@NullMarked
public final class Status {

    private Status() {}

    public static Node of(boolean active) {
        return span(cls(active ? "badge active" : "badge inactive"), text(message(active ? "Ativo" : "Inativo")));
    }
}
