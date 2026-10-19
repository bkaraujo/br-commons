package br.commons.annotation;

import br.commons.Strings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public abstract class Entity implements Comparable<Entity> {
    private String name = Strings.EMPTY;
    private String description = Strings.EMPTY;

    public abstract UUID identity();

    public final String name() { return name; }
    public final void name(String name) { this.name = name; }

    public final String description() { return description; }
    public final void description(String description) { this.description = description; }

    @Override
    public int compareTo(Entity other) {
        return identity().compareTo(other.identity());
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (object == null) return false;
        if (this == object) return true;
        if (object instanceof Entity other) {
            return identity().equals(other.identity());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return identity().hashCode();
    }
}
