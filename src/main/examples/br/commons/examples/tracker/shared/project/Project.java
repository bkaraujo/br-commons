package br.commons.examples.tracker.shared.project;

import br.commons.examples.tracker.shared.registry.Registered;
import org.jspecify.annotations.NullMarked;

/** A project hours are worked on; an inactive one keeps its entries but no longer takes new ones. */
@NullMarked
public record Project(int id, String name, String client, boolean active) implements Registered {

    public static Project of(int id, ProjectInput input) {
        return new Project(id, input.name(), input.client(), true);
    }

    public Project with(ProjectInput input) {
        return new Project(id, input.name(), input.client(), active);
    }

    public Project toggled() {
        return new Project(id, name, client, !active);
    }
}
