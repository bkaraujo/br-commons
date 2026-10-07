package br.commons.lang.html;

import br.commons.lang.html.form.Violation;
import br.commons.locale.i18n.Text;

import java.util.List;
import java.util.Map;

import static br.commons.lang.Html4.*;

/** Example HTML 4.01 Strict page: the same regions as plain {@code div}, which is what this version has. */
final class SignUpPage4 {

    private SignUpPage4() {}

    static String render(Map<String, List<String>> values, List<Violation> violations) {
        return document(
                html(lang("pt-BR"),
                        head(metaCharset("utf-8"), title(text(Text.message("Cadastro")))),
                        body(
                                div(h1(text(Text.message("Cadastro")))),
                                div(SignUpForm.render(DIALECT, values, violations)),
                                div(small(text(Text.message("Sem CSS e sem JS")))))));
    }
}
