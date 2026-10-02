package br.commons;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@NullMarked
public abstract class RT {
    private RT(){}

    public static volatile boolean running = false;

    /**
     * <p>
     * Package prefixes treated as "framework"/infrastructure by {@link Meta},
     * used to discard irrelevant frames when locating user code:
     * <ul>
     *   <li>{@link Meta#stackFrame()} — filters these frames before resolving
     *       the real caller (e.g. log origin);</li>
     *   <li>{@link Meta#userMainClassName()} — walks the stack from bottom to top
     *       looking for the first class whose name does NOT start with any of these
     *       prefixes, treating it as the user entry point.</li>
     * </ul>
     * </p>
     * <p>
     * Mutable at runtime: bootstrap/tests can register additional prefixes
     * (e.g. packages from another module) so they are also treated as
     * infrastructure.</p>
     *
     * <p>That's why {@code Meta.isStackFrameExcluded} does not cache
     * the decision per class — cache would be bound to the state of this list at the
     * first query and would ignore subsequent additions.
     * </p>
     */
    public static final List<String> packages = new CopyOnWriteArrayList<>(
            List.of(
                    "java.",
                    "javax.",
                    "jdk.",
                    "sun.",
                    "br.commons.logger.",
                    "br.commons.Logger",
                    "br.commons.Meta",
                    "br.commons.debug.Execution"
            )
    );
}

