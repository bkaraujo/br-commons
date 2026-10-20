package br.bundled.money.web.ui;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The themes of the switch. The choice lives only in the browser ({@code static/money/theme.js}: localStorage
 * and {@code <html data-theme>}); the server just renders the buttons. {@link #AUTO} follows the system.
 */
@NullMarked
enum Theme {

    AUTO("Automático", "◐"),
    LIGHT("Claro", "☀"),
    DARK("Escuro", "☾");

    private final String label;
    private final String icon;

    Theme(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    /** The value of {@code data-theme} and of the button's {@code data-theme-choice}. */
    String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    String label() {
        return label;
    }

    String icon() {
        return icon;
    }
}
