package br.bundled.money.web.ui;

import br.commons.Strings;
import br.commons.web.ui.FontAwesome;
import org.jspecify.annotations.NullMarked;

/**
 * The themes of the switch. The choice lives only in the browser ({@code static/money/theme.js}: localStorage
 * and {@code <html data-theme>}); the server just renders the buttons. {@link #AUTO} follows the system.
 */
@NullMarked
enum Theme {

    AUTO(FontAwesome.Solid.CIRCLE_HALF_STROKE),
    LIGHT(FontAwesome.Solid.SUN),
    DARK(FontAwesome.Solid.MOON);

    private final FontAwesome.Icon icon;

    Theme(FontAwesome.Icon icon) {
        this.icon = icon;
    }

    /** The value of {@code data-theme} and of the button's {@code data-theme-choice}. */
    String token() {
        return Strings.lower(name());
    }

    /** Its name ({@code forms.theme.<THEME>}). */
    String label() {
        return Ui.string(Ui.PREFIX + "theme." + name());
    }

    FontAwesome.Icon icon() {
        return icon;
    }
}
