package br.commons.html.core;

import br.commons.locale.i18n.Errors;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/** A piece of HTML. Rendering escapes every text and attribute value; only {@link Raw} is written as is. */
@NullMarked
public sealed interface Node extends Content permits Node.Element, Node.Text, Node.Fragment, Node.Raw {

    void render(StringBuilder out);

    default String render() {
        val out = new StringBuilder(256);
        render(out);
        return out.toString();
    }

    /**
     * An element; void elements ({@code input}, {@code meta}…) have no children and no closing tag. The void set
     * is the union of HTML 4 and 5: no tag is void in one version and a container in the other.
     */
    @NullMarked
    record Element(String tag, List<Attribute> attributes, List<Node> children) implements Node {

        private static final Set<String> VOID = Set.of("area", "base", "basefont", "br", "col", "embed", "frame",
                "hr", "img", "input", "isindex", "link", "meta", "param", "source", "track", "wbr");

        /** Tag names are written raw: {@code [A-Za-z][A-Za-z0-9-]*} (custom elements included), checked once here. */
        public Element {
            if (!Names.isTag(tag)) throw new IllegalArgumentException(Errors.get().invalidTagName(tag).value());
        }

        @Override
        public void render(StringBuilder out) {
            out.append('<').append(tag);
            attributes.forEach(attribute -> attribute.render(out));
            out.append('>');
            if (VOID.contains(tag)) {
                return;
            }
            children.forEach(child -> child.render(out));
            out.append("</").append(tag).append('>');
        }
    }

    @NullMarked
    record Text(String text) implements Node {
        @Override
        public void render(StringBuilder out) {
            Escape.into(out, text);
        }
    }

    @NullMarked
    record Fragment(List<Node> nodes) implements Node {
        @Override
        public void render(StringBuilder out) {
            nodes.forEach(node -> node.render(out));
        }
    }

    /**
     * Markup written without escaping; created only in {@code br.commons.html} ({@code Html#raw},
     * {@code scriptText}, {@code styleText}), which {@code HtmlArchitectureTest} enforces.
     */
    @NullMarked
    record Raw(String markup) implements Node {
        @Override
        public void render(StringBuilder out) {
            out.append(markup);
        }
    }
}
