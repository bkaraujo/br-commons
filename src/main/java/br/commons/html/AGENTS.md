# br.commons.html — DSL HTML 4/5 tipada, com escape e formulários

Monta HTML por funções estáticas que devolvem `Node`; renderização escapa todo
texto e valor de atributo. Formulários declarados uma vez (`Field`) servem para
renderizar e para validar no servidor.

Referências:
- HTML 5: https://html.spec.whatwg.org/dev/
- HTML 4: https://www.w3.org/TR/html401/

| Tipo / subpacote       | Papel                                                     |
|------------------------|-----------------------------------------------------------|
| `Html`                 | DSL comum às duas versões (elementos, atributos, nós)     |
| `html5/Html5`          | `Html` + elementos/atributos só do HTML 5                 |
| `html4/Html4`          | `Html` + elementos/atributos só do HTML 4.01 Strict       |
| `Dialect`              | Doctype, `meta charset`, `type` e restrições por versão   |
| `html5/Html5Dialect`, `html4/Html4Dialect` | `INSTANCE` de cada dialeto        |
| `core/Node`            | `Element`, `Text`, `Fragment`, `Raw`; `render()`          |
| `core/Attribute`       | Nome + valor (`null` = atributo booleano)                 |
| `core/Keyword`         | Enum de valor de atributo (`token()`)                     |
| `form/FieldSpec`→`Field` | Declaração e campo validado                             |
| `form/Validation`      | Checagem no servidor → `List<Violation>`                  |
| enums na raiz          | `FormMethod`, `ButtonType`, `Direction`, `FormEnctype`...  |

## 1. Página

```java
import static br.commons.html.html5.Html5.*;

String page = document(Html5Dialect.INSTANCE,
    html(lang("pt-BR"),
        head(metaCharset(Html5Dialect.INSTANCE, "UTF-8"), title(text(msg.title()))),
        body(cls("app"),
            h1(text(msg.title())),
            ul(each(items, it -> li(text(Text.data(it.name()))))),
            when(user.isAdmin(), () -> a(href("/admin"), text(msg.admin()))))));
```

- Um `import static` traz a versão inteira (membros estáticos herdados).
- Conteúdo de elemento = `Attribute`s e `Node`s em qualquer ordem.
- `text(...)` só aceita `br.commons.i18n.Text`: interface via bundle; dado via
  `Text.data(...)`.
- Renomes: `class`→`cls`, `for`→`htmlFor`, `http-equiv`→`httpEquiv`.
- Atributos lidos por pessoas (`title`, `alt`, `label`, `abbr`) recebem `Text`.

## 2. Formulário

```java
Field email = FieldSpec.of("email", InputType.EMAIL).asRequired().withMaxLength(120).build().getOrThrow();
Field age   = FieldSpec.of("age", InputType.NUMBER).withMin("18").withMax("120").build().getOrThrow();

form(method(FormMethod.POST), action("/signup"),
    label(htmlFor("email"), text(msg.email())),
    input(dialect, email, current.getOrDefault("email", ""), id("email")),
    select(dialect, plan, List.of(new Option("pro", msg.pro())), Set.of(selected)),
    textarea(dialect, bio, currentBio),
    button(type(ButtonType.SUBMIT), text(msg.send())));
```

Validação no servidor (obrigatória — HTML 4 não valida e HTML 5 é burlável):

```java
List<Violation> errors = Validation.check(List.of(email, age), form::all);
errors.forEach(v -> mensagens.put(v.field(), msg.rule(v.rule())));  // REQUIRED, INVALID, TOO_LONG...
```

## 3. Utilitários de nó

```java
fragment(a, b, c);                    // vários nós sem wrapper
each(lista, item -> li(...));         // laço
when(cond, () -> nodo);               // condicional (lazy)
element("my-widget", attribute("x", "1"));   // tag sem fábrica
raw(markup);                          // SEM escape — restrito por ArchUnit
```

## 4. Regras rápidas para agentes

1. Nunca `raw(...)` com dado externo; só classes revisadas (ArchUnit).
2. Dado do usuário entra via `Text.data`, nunca concatenado em markup.
3. Validação sempre com `Validation.check`, mesmo com HTML 5.
4. `Field` só via `FieldSpec.build()` (declaração inconsistente → `Failure`).
5. View multi-versão importa `Html` e recebe `Dialect`; senão, importe
   `Html5`/`Html4` diretamente.
6. Nome de tag/atributo nunca vem de dado externo (não é escapado).

## 5. Checklist de revisão

- [ ] Sem `raw` novo fora de classes liberadas.
- [ ] Todo texto visível é `Text` (bundle ou `data`).
- [ ] Campos do form validados no POST com os mesmos `Field`s.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 6. Renderização

- `Element.render`: `<tag` + atributos + `>`; elementos void (união HTML 4 e
  5: `area, base, basefont, br, col, embed, frame, hr, img, input, isindex,
  link, meta, param, source, track, wbr`) não têm filhos nem fechamento —
  filhos passados a um void são **descartados em silêncio**.
- `Escape.into` troca `& < > " '` por entidades, igual para texto e atributo
  (atributos sempre entre aspas duplas).
- **Não escapados**: nome de tag e nome de atributo. `element(tagExterno)` ou
  `hxOn(eventoExterno, ...)` permitem injeção.
- `<script>`/`<style>` inline: use `scriptText(js)`/`styleText(css)` (sem
  escape; recusam `</script`/`</style`). `text(...)` escaparia e quebraria o
  código. Só código do desenvolvedor, nunca dado; prefira arquivo estático.
- Valores de URL (`href`, `src`, `action`) são só escapados: `javascript:`
  não é bloqueado. Valide esquema de URLs vindas de dados.
- `document(dialect, root)` = doctype + render em `StringBuilder(4096)`.

## 7. Formulários e dialetos

- `control(...)`: `type` (via `dialect.inputType`), `name` e
  `dialect.constraints(tag, field)` (`required`, `minlength`, `pattern`,
  `min`, `max`, `step` no HTML 5; HTML 4 cai para `text` e não emite
  restrições nativas).
- `input`: não ecoa valor de `PASSWORD`/`FILE`.
- `textarea`: escreve `\n` antes do valor (o parser HTML descarta um newline
  inicial; assim o valor que começa com newline sobrevive).
- `select`: `multiple` automático se `field.multiple()`.

## 8. `Validation`

- Valor vazio só falha `REQUIRED` (como o navegador).
- Ordem: tipo (`INVALID` via `InputType.read`) → texto (`TOO_SHORT`,
  `TOO_LONG`, `PATTERN`) → significado (`BELOW_MIN`, `ABOVE_MAX`, `STEP`).
  Retorna a **primeira** regra violada por campo.
- `step` conta a partir de `min` (ou zero); `any`/ausente desliga.
- E-mail múltiplo chega separado por vírgula num valor só.
- `Attempt` é o único ponto que captura exceção de parser do JDK
  (`LocalDate.parse`, `BigDecimal`, `Pattern.compile`) → `Result`.
- `FieldSpec.build` valida a declaração: regex compilável, limites
  coerentes com o tipo (`InputKind`: textual × ordenado × nenhum).

## 9. Arquitetura

- `Html` é abstrato; `Html4`/`Html5` estendem (herdam estáticos). Elemento de
  uma versão só aparece na classe dela.
- `Raw` só é criado por `Html.raw`, restrito por ArchUnit (spec §14.1).
- `br.commons.htmx` depende deste pacote; o contrário é proibido
  (`HtmlArchitectureTest`).

## 10. Extensão

| Necessidade                  | Onde mexer                                         |
|------------------------------|----------------------------------------------------|
| Elemento comum às 2 versões  | `Html` (javadoc com links das duas specs)          |
| Elemento só HTML 5           | `html5/Html5`                                      |
| Valor enumerado de atributo  | enum implementando `Keyword`                       |
| Novo `InputType`             | `InputType` (+ `read`, `kind`) e dialetos          |
| Nova regra de validação      | `ValidationRule` + `Validation` + `FieldSpec`      |
