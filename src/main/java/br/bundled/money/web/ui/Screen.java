package br.bundled.money.web.ui;

import org.jspecify.annotations.NullMarked;

/** The screens of the menu. */
@NullMarked
public enum Screen {

    HOME("/", "Minhas contas", "◎"),
    ACCOUNTS("/accounts", "Plano de contas", "☰"),
    STATEMENT("/statement", "Extrato", "≡"),
    ENTRY("/entries/new", "Lançar", "+"),
    REPORTS("/reports", "Relatórios", "▤");

    private final String path;
    private final String label;
    private final String icon;

    Screen(String path, String label, String icon) {
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

    public String icon() {
        return icon;
    }
}
