package br.commons.html.html5;

import br.commons.html.Dialect;
import br.commons.html.core.Attribute;
import br.commons.html.core.Node;
import br.commons.html.form.Field;
import br.commons.html.form.InputType;
import br.commons.html.form.Validation;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML 5: every input type is native and the field constraints become the browser's own check
 * ({@code required}, {@code minlength}, {@code maxlength}, {@code pattern}, {@code min}, {@code max},
 * {@code step}, {@code multiple}), each on the controls it applies to. The server still checks
 * ({@link Validation}).
 */
@NullMarked
public final class Html5Dialect implements Dialect {

    public static final Html5Dialect INSTANCE = new Html5Dialect();

    private Html5Dialect() {}

    @Override
    public String doctype() {
        return "<!DOCTYPE html>";
    }

    @Override
    public Node.Element metaCharset(String encoding) {
        return new Node.Element("meta", List.of(new Attribute("charset", encoding)), List.of());
    }

    @Override
    public String inputType(InputType type) {
        return type.token();
    }

    @Override
    public List<Attribute> constraints(String tag, Field field) {
        val attributes = new ArrayList<Attribute>();
        if (field.required()) {
            attributes.add(new Attribute("required", null));
        }
        if ("textarea".equals(tag)) {
            text(field, false, attributes);
        } else if ("input".equals(tag)) {
            input(field, attributes);
        }
        return List.copyOf(attributes);
    }

    private static void input(Field field, List<Attribute> attributes) {
        val type = field.type();
        if (type.textual()) {
            text(field, true, attributes);
        } else if (type.ordered()) {
            range(field, attributes);
        }
        if (field.multiple() && (type == InputType.EMAIL || type == InputType.FILE)) {
            attributes.add(new Attribute("multiple", null));
        }
    }

    /** Lengths on text inputs and textarea; {@code pattern} on inputs only. */
    private static void text(Field field, boolean input, List<Attribute> attributes) {
        if (field.minLength() != null) {
            attributes.add(new Attribute("minlength", String.valueOf(field.minLength())));
        }
        if (field.maxLength() != null) {
            attributes.add(new Attribute("maxlength", String.valueOf(field.maxLength())));
        }
        if (input && field.pattern() != null) {
            attributes.add(new Attribute("pattern", field.pattern()));
        }
    }

    private static void range(Field field, List<Attribute> attributes) {
        if (field.min() != null) {
            attributes.add(new Attribute("min", field.min()));
        }
        if (field.max() != null) {
            attributes.add(new Attribute("max", field.max()));
        }
        if (field.step() != null) {
            attributes.add(new Attribute("step", field.step()));
        }
    }
}
