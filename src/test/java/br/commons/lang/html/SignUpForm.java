package br.commons.lang.html;

import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.*;
import br.commons.locale.i18n.Text;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.commons.lang.html.Html.*;

/**
 * Example view common to HTML 4 and 5: written with the common DSL ({@code import static Html.*}); where the
 * version matters (the form controls) it passes the {@link Dialect}.
 */
final class SignUpForm {

    static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    static final Field EMAIL = FieldSpec.of("email", InputType.EMAIL).asRequired().build().getOrThrow();
    static final Field BIRTH = FieldSpec.of("birth", InputType.DATE).withMin("1900-01-01").withMax("2026-12-31")
            .build().getOrThrow();
    static final Field PLAN = FieldSpec.of("plan", InputType.TEXT).asRequired().build().getOrThrow();
    static final List<Field> FIELDS = List.of(NAME, EMAIL, BIRTH, PLAN);

    private static final List<Option> PLANS = List.of(
            new Option("basic", Text.message("Básico")), new Option("full", Text.message("Completo")));

    private SignUpForm() {}

    /** The form with the submitted values and the violations found; empty maps for the first visit. */
    static Node render(Dialect dialect, Map<String, List<String>> values, List<Violation> violations) {
        return form(action("/cadastro"), method(FormMethod.POST),
                row(NAME, "Nome", input(dialect, NAME, first(values, NAME), id("name")), violations),
                row(EMAIL, "E-mail", input(dialect, EMAIL, first(values, EMAIL), id("email")), violations),
                row(BIRTH, "Nascimento", input(dialect, BIRTH, first(values, BIRTH), id("birth")), violations),
                row(PLAN, "Plano", select(dialect, PLAN, PLANS, Set.copyOf(values.getOrDefault("plan", List.of())),
                        id("plan")), violations),
                p(button(type(ButtonType.SUBMIT), text(Text.message("Enviar")))));
    }

    /** Label, control and the field's error; a real view takes the label and the message from a bundle. */
    private static Node row(Field field, String label, Node control, List<Violation> violations) {
        return p(label(htmlFor(field.name()), text(Text.message(label))), control,
                each(violations.stream().filter(violation -> violation.field().equals(field.name())).toList(),
                        violation -> strong(text(Text.message(violation.rule().name())))));
    }

    private static String first(Map<String, List<String>> values, Field field) {
        val list = values.getOrDefault(field.name(), List.of());
        return list.isEmpty() ? "" : list.getFirst();
    }
}
