package br.bundled.tracker.shared.person;

import br.bundled.tracker.shared.registry.Registered;
import org.jspecify.annotations.NullMarked;

/** A person who works hours; an inactive one keeps their entries but no longer takes new ones. */
@NullMarked
public record Person(int id, String name, String email, boolean active) implements Registered {

    public static Person of(int id, PersonInput input) {
        return new Person(id, input.name(), input.email(), true);
    }

    public Person with(PersonInput input) {
        return new Person(id, input.name(), input.email(), active);
    }

    public Person toggled() {
        return new Person(id, name, email, !active);
    }
}
