package br.commons.lang.html.html4;

import br.commons.lang.html.Dialect;
import br.commons.lang.html.core.Attribute;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.InputType;
import br.commons.lang.html.form.Validation;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * HTML 4.01 Strict: the ten input types of the version, every other one rendered as {@code text}, and no browser
 * check besides {@code maxlength} on text and password. Constraints are not hinted with HTML 5 attributes, which
 * a current browser would enforce anyway: under this dialect the server ({@link Validation})
 * is the only check.
 */
@NullMarked
public final class Html4Dialect implements Dialect {

    public static final Html4Dialect INSTANCE = new Html4Dialect();

    private static final String DOCTYPE =
            "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">";

    private static final Set<InputType> NATIVE = EnumSet.of(InputType.TEXT, InputType.PASSWORD, InputType.CHECKBOX,
            InputType.RADIO, InputType.SUBMIT, InputType.RESET, InputType.FILE, InputType.HIDDEN, InputType.IMAGE,
            InputType.BUTTON);

    private Html4Dialect() {}

    @Override
    public String doctype() {
        return DOCTYPE;
    }

    @Override
    public Node.Element metaCharset(String encoding) {
        return new Node.Element("meta", List.of(new Attribute("http-equiv", "Content-Type"),
                new Attribute("content", "text/html; charset=" + encoding)), List.of());
    }

    @Override
    public String inputType(InputType type) {
        return NATIVE.contains(type) ? type.token() : InputType.TEXT.token();
    }

    /** Only {@code maxlength}, on an input rendered as text or password. */
    @Override
    public List<Attribute> constraints(String tag, Field field) {
        if (!"input".equals(tag) || field.maxLength() == null || !field.type().textual()) {
            return List.of();
        }
        return List.of(new Attribute("maxlength", String.valueOf(field.maxLength())));
    }
}
