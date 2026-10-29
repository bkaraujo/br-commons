package br.bundled.judas._2_infrastructure.web.views;

import br.commons.Strings;
import br.commons.web.ui.FontAwesome;
import org.jspecify.annotations.NullMarked;

/** The choices of the theme switch; the choice itself lives in the browser (UI-80). */
@NullMarked
enum Theme {

    AUTO(FontAwesome.Solid.CIRCLE_HALF_STROKE),
    LIGHT(FontAwesome.Solid.SUN),
    DARK(FontAwesome.Solid.MOON);

    private final FontAwesome.Icon icon;

    Theme(FontAwesome.Icon icon) { this.icon = icon; }

    /** The value of {@code <html data-theme>} the button applies. */
    String token() { return Strings.lower(name()); }

    /** The name of the choice ({@code forms.theme.<THEME>}). */
    String label() { return Ui.string("forms.theme." + name()); }

    FontAwesome.Icon icon() { return icon; }
}
