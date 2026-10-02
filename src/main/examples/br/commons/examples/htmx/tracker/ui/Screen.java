package br.commons.examples.htmx.tracker.ui;

import org.jspecify.annotations.NullMarked;

/** The screens of the menu. */
@NullMarked
public enum Screen {

    WEEK("/", "Semana"),
    PROJECTS("/projects", "Projetos"),
    PEOPLE("/people", "Pessoas");

    private final String path;
    private final String label;

    Screen(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }
}
