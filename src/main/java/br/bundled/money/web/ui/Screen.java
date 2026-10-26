package br.bundled.money.web.ui;

import br.commons.web.ui.FontAwesome;
import org.jspecify.annotations.NullMarked;

/** The screens of the menu. */
@NullMarked
public enum Screen {

    HOME("/", FontAwesome.Solid.HOME_USER),
    STATEMENT("/statement", FontAwesome.Solid.LIST),
    INSTALLMENTS("/installments", FontAwesome.Solid.CREDIT_CARD),
    ENTRY("/entries/new", FontAwesome.Solid.PLUS),
    ACCOUNTS("/accounts", FontAwesome.Solid.SITEMAP),
    REPORTS("/reports", FontAwesome.Solid.CHART_PIE);

    private final String path;
    private final FontAwesome.Icon icon;

    Screen(String path, FontAwesome.Icon icon) {
        this.path = path;
        this.icon = icon;
    }

    public String path() {
        return path;
    }

    /** The name on the menu and in the title ({@code forms.screen.<SCREEN>}). */
    public String label() {
        return Ui.string(Ui.PREFIX + "screen." + name());
    }

    public FontAwesome.Icon icon() {
        return icon;
    }
}
