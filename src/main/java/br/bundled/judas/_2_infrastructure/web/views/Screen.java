package br.bundled.judas._2_infrastructure.web.views;

import br.commons.web.ui.FontAwesome;
import org.jspecify.annotations.NullMarked;

/** The screens on the menu, in its order (UI-12). */
@NullMarked
public enum Screen {

    HOME("/", FontAwesome.Solid.HOME);

    private final String path;
    private final FontAwesome.Icon icon;

    Screen(String path, FontAwesome.Icon icon) {
        this.path = path;
        this.icon = icon;
    }

    /** The URL that opens the screen. */
    public String path() { return path; }

    /** The decorative icon beside the label. */
    public FontAwesome.Icon icon() { return icon; }

    /** The name on the menu and in the title ({@code forms.screen.<SCREEN>}). */
    public String label() { return Ui.string("forms.screen." + name()); }
}
