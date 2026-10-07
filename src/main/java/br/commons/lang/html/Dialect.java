package br.commons.lang.html;

import br.commons.lang.html.core.Attribute;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.InputType;
import br.commons.lang.html.form.Validation;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Strategy for what changes between HTML versions: the doctype and how a {@link Field} reaches the browser
 * ({@code type} of the input and the constraint attributes it enforces). {@link Html} is the context that
 * builds the controls; the implementations live in {@code br.commons.lang.html.html4} and
 * {@code br.commons.lang.html.html5}. Server-side checking does not vary: it is {@link Validation}, for both.
 */
@NullMarked
public interface Dialect {

    /** The doctype that starts the page. */
    String doctype();

    /** The {@code meta} that declares the encoding: {@code charset} in HTML 5, {@code http-equiv} in HTML 4. */
    Node.Element metaCharset(String encoding);

    /** The {@code type} attribute for the input: the native one, or the fallback the dialect knows. */
    String inputType(InputType type);

    /**
     * Attributes the browser enforces for the field constraints on this control ({@code input},
     * {@code textarea} or {@code select}); none where the dialect has no such check.
     */
    List<Attribute> constraints(String tag, Field field);

    /** The whole page, with the doctype. */
    default String document(Node root) {
        val out = new StringBuilder(4096).append(doctype());
        root.render(out);
        return out.toString();
    }
}
