package br.commons.html;

import br.commons.html.form.Validation;
import br.commons.html.form.ValidationRule;
import br.commons.html.form.Violation;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static br.commons.html.SignUpForm.FIELDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example of use: a plain sign-up page (no CSS, no JS) in HTML 4 and HTML 5. The form ({@link SignUpForm}) is
 * written once with the common DSL; each page ({@link SignUpPage5}, {@link SignUpPage4}) statically imports its
 * version and lays the form out with the elements of that version. The dialect decides the doctype, the
 * {@code meta} charset, the input types and which constraints the browser checks, while {@link Validation} checks
 * the submission the same way for both.
 */
class DialectPageTest {

    // ---------- the same page in each dialect

    @Test
    void html5PageUsesNativeTypesAndBrowserChecks() {
        assertEquals(Markup.flat("""
                <!DOCTYPE html>
                <html lang="pt-BR">
                  <head>
                    <meta charset="utf-8">
                    <title>Cadastro</title>
                  </head>
                  <body>
                    <header><h1>Cadastro</h1></header>
                    <main>
                      <form action="/cadastro" method="post">
                        <p>
                          <label for="name">Nome</label>
                          <input type="text" name="name" required maxlength="60" id="name">
                        </p>
                        <p>
                          <label for="email">E-mail</label>
                          <input type="email" name="email" required id="email">
                        </p>
                        <p>
                          <label for="birth">Nascimento</label>
                          <input type="date" name="birth" min="1900-01-01" max="2026-12-31" id="birth">
                        </p>
                        <p>
                          <label for="plan">Plano</label>
                          <select name="plan" required id="plan">
                            <option value="basic">Básico</option>
                            <option value="full">Completo</option>
                          </select>
                        </p>
                        <p><button type="submit">Enviar</button></p>
                      </form>
                    </main>
                    <footer><small>Sem CSS e sem JS</small></footer>
                  </body>
                </html>
                """), SignUpPage5.render(Map.of(), List.of()));
    }

    @Test
    void html4PageFallsBackToTextAndLeavesChecksToTheServer() {
        assertEquals(Markup.flat("""
                <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
                <html lang="pt-BR">
                  <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                    <title>Cadastro</title>
                  </head>
                  <body>
                    <div><h1>Cadastro</h1></div>
                    <div>
                      <form action="/cadastro" method="post">
                        <p>
                          <label for="name">Nome</label>
                          <input type="text" name="name" maxlength="60" id="name">
                        </p>
                        <p>
                          <label for="email">E-mail</label>
                          <input type="text" name="email" id="email">
                        </p>
                        <p>
                          <label for="birth">Nascimento</label>
                          <input type="text" name="birth" id="birth">
                        </p>
                        <p>
                          <label for="plan">Plano</label>
                          <select name="plan" id="plan">
                            <option value="basic">Básico</option>
                            <option value="full">Completo</option>
                          </select>
                        </p>
                        <p><button type="submit">Enviar</button></p>
                      </form>
                    </div>
                    <div><small>Sem CSS e sem JS</small></div>
                  </body>
                </html>
                """), SignUpPage4.render(Map.of(), List.of()));
    }

    // ---------- the post: server check, then the page again with values and errors

    @Test
    void submissionIsCheckedTheSameWayAndThePageShowsTheErrors() {
        val submitted = Map.of("name", List.of(""), "email", List.of("x"), "birth", List.of("1800-01-01"),
                "plan", List.of("full"));
        val violations = Validation.check(FIELDS, name -> submitted.getOrDefault(name, List.of()));
        assertEquals(List.of(new Violation("name", ValidationRule.REQUIRED), new Violation("email", ValidationRule.INVALID),
                new Violation("birth", ValidationRule.BELOW_MIN)), violations);

        val html4 = SignUpPage4.render(submitted, violations);
        assertTrue(html4.contains(Markup.flat("""
                <input type="text" name="name" maxlength="60" id="name">
                <strong>REQUIRED</strong>
                """)));
        assertTrue(html4.contains(Markup.flat("""
                <input type="text" name="email" value="x" id="email">
                <strong>INVALID</strong>
                """)));
        assertTrue(html4.contains(Markup.flat("""
                <input type="text" name="birth" value="1800-01-01" id="birth">
                <strong>BELOW_MIN</strong>
                """)));
        assertTrue(html4.contains("""
                <option value="full" selected>Completo</option>"""));

        val html5 = SignUpPage5.render(submitted, violations);
        assertTrue(html5.contains(Markup.flat("""
                <input type="email" name="email" required value="x" id="email">
                <strong>INVALID</strong>
                """)));
        assertTrue(html5.contains(Markup.flat("""
                <input type="date" name="birth" min="1900-01-01" max="2026-12-31" value="1800-01-01" id="birth">
                <strong>BELOW_MIN</strong>
                """)));

        val valid = Map.of("name", List.of("Ana"), "email", List.of("ana@br.dev"), "birth", List.of("1990-05-10"),
                "plan", List.of("basic"));
        assertEquals(List.of(), Validation.check(FIELDS, name -> valid.getOrDefault(name, List.of())));
    }
}
