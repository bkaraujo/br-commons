package br.commons.lang.htmx;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A swap style with its modifiers, as {@code hx-swap} and {@code HX-Reswap} take it:
 * {@code SwapSpec.of(HxSwap.OUTER_HTML).settle(Duration.ofMillis(100)).show(ScrollEdge.TOP)} renders
 * {@code outerHTML settle:100ms show:top}. Immutable: each modifier gives a new spec.
 *
 * @see <a href="https://htmx.org/attributes/hx-swap/">htmx: hx-swap (styles and modifiers)</a>
 */
@NullMarked
public final class SwapSpec {

    private final HxSwap style;
    private final List<String> modifiers;

    private SwapSpec(HxSwap style, List<String> modifiers) {
        this.style = style;
        this.modifiers = modifiers;
    }

    public static SwapSpec of(HxSwap style) {
        return new SwapSpec(style, List.of());
    }

    /** Uses the View Transitions API for the swap. */
    public SwapSpec transition() { return with("transition:true"); }

    /** Waits before swapping the new content in. */
    public SwapSpec swap(Duration delay) { return with("swap:" + delay.toMillis() + "ms"); }

    /** Waits between the swap and the settle step. */
    public SwapSpec settle(Duration delay) { return with("settle:" + delay.toMillis() + "ms"); }

    /** Keeps the page title even if the response has a {@code title}. */
    public SwapSpec ignoreTitle() { return with("ignoreTitle:true"); }

    /** Scrolls the target to the edge. */
    public SwapSpec scroll(ScrollEdge edge) { return with("scroll:" + edge.token()); }

    /** Scrolls the element matching {@code selector} (or {@code window}) to the edge. */
    public SwapSpec scroll(String selector, ScrollEdge edge) { return with("scroll:" + selector + ":" + edge.token()); }

    /** Shows the target's edge in the viewport. */
    public SwapSpec show(ScrollEdge edge) { return with("show:" + edge.token()); }

    /** Shows the edge of the element matching {@code selector} (or {@code window}). */
    public SwapSpec show(String selector, ScrollEdge edge) { return with("show:" + selector + ":" + edge.token()); }

    /** Whether to scroll to a focused element after the swap. */
    public SwapSpec focusScroll(boolean scroll) { return with("focus-scroll:" + scroll); }

    public String token() {
        return modifiers.isEmpty() ? style.token() : style.token() + " " + String.join(" ", modifiers);
    }

    @Override
    public String toString() {
        return token();
    }

    private SwapSpec with(String modifier) {
        val next = new ArrayList<>(modifiers);
        next.add(modifier);
        return new SwapSpec(style, List.copyOf(next));
    }
}
