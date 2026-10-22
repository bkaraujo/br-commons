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

    AUTO("Automático", FontAwesome.Solid.CIRCLE_HALF_STROKE),
    LIGHT("Claro", FontAwesome.Solid.SUN),
    DARK("Escuro", FontAwesome.Solid.MOON);

    private final String label;
    private final FontAwesome.Icon icon;

    Theme(String label, FontAwesome.Icon icon) {
        this.label = label;
        this.icon = icon;
    }

    /** The value of {@code data-theme} and of the button's {@code data-theme-choice}. */
    String token() {
        return Strings.lower(name());
    }

    String label() {
        return label;
    }

    FontAwesome.Icon icon() {
        return icon;
    }
}
