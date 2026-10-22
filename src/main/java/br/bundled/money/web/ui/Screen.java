package br.bundled.money.web.ui;

import br.commons.web.ui.FontAwesome;
import org.jspecify.annotations.NullMarked;

/** The screens of the menu. */
@NullMarked
public enum Screen {

    HOME("/", "Visão Geral", FontAwesome.Solid.HOME_USER),
    STATEMENT("/statement", "Extrato", FontAwesome.Solid.LIST),
    INSTALLMENTS("/installments", "Parcelamentos", FontAwesome.Solid.CREDIT_CARD),
    ENTRY("/entries/new", "Lançar", FontAwesome.Solid.PLUS),
    ACCOUNTS("/accounts", "Plano de contas", FontAwesome.Solid.SITEMAP),
    REPORTS("/reports", "Relatórios", FontAwesome.Solid.CHART_PIE);

    private final String path;
    private final String label;
    private final FontAwesome.Icon icon;

    Screen(String path, String label, FontAwesome.Icon icon) {
        this.path = path;
        this.label = label;
        this.icon = icon;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }

    public FontAwesome.Icon icon() {
        return icon;
    }
}
