package br.commons.html;

import br.commons.html.form.Violation;
import br.commons.locale.i18n.Text;

import java.util.List;
import java.util.Map;

import static br.commons.html.html5.Html5.*;

/** Example HTML 5 page: one static import brings the common DSL and the HTML 5 elements. */
final class SignUpPage5 {

    private SignUpPage5() {}

    static String render(Map<String, List<String>> values, List<Violation> violations) {
        return document(
                html(lang("pt-BR"),
                        head(metaCharset("utf-8"), title(text(Text.message("Cadastro")))),
                        body(
                                header(h1(text(Text.message("Cadastro")))),
                                main(SignUpForm.render(DIALECT, values, violations)),
                                footer(small(text(Text.message("Sem CSS e sem JS")))))));
    }
}
