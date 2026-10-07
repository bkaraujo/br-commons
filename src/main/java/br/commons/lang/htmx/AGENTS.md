# br.commons.lang.htmx — suporte a htmx

Atributos `hx-*` como DSL estática ao lado da DSL HTML, e leitura/escrita dos
headers `HX-*`. Depende de `br.commons.lang.html` e da camada HTTP
(`br.commons.protocol.http`); nenhum deles depende deste pacote, e ele não
conhece `br.commons.web` (`HtmlArchitectureTest`). A fachada dos atributos
(`Htmx`) fica em `br.commons.lang`; aqui ficam enums, `SwapSpec` e os headers.

Referência: https://htmx.org/reference/

| Tipo            | Papel                                                      |
|-----------------|------------------------------------------------------------|
| `lang.Htmx`     | `hxGet`, `hxPost`, `hxTarget`, `hxSwap`, `hxTrigger`, ... → `Attribute`; `hxPartial(...)` → elemento `<hx-partial>` |
| `HxSwap`        | `innerHTML`, `outerHTML`, `textContent`, `beforebegin`, ... `delete`, `none` |
| `SwapSpec`      | Swap + modificadores (`swap`, `settle`, `show`, `scroll`, `transition`, `ignoreTitle`, `focusScroll`) |
| `ScrollEdge`    | `top`/`bottom` para `scroll`/`show`                        |
| `HxSync`        | Estratégias de `hx-sync` (`drop`, `abort`, `replace`, `queue ...`) |
| `HtmxRequest`   | Lê `HX-Request`, `HX-Target`, `HX-Trigger`, `HX-Trigger-Name`, `HX-Boosted`, `HX-History-Restore-Request`... |
| `HtmxResponse`  | Escreve `HX-Location`, `HX-Retarget`, `HX-Reswap`, `HX-Trigger`... |

## 1. Atributos na view

```java
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

button(hxPost("/likes"), hxTarget("#likes"), hxSwap(HxSwap.OUTER_HTML),
       hxConfirm(msg.sure()), text(msg.like()));

input(name("q"), hxGet("/search"), hxTrigger("keyup changed delay:300ms"),
      hxTarget("#results"), hxIndicator("#spinner"), hxSync("closest form", HxSync.REPLACE));

form(hxPost("/upload"), hxEncodingMultipart(), ...);
div(hxSwap(SwapSpec.of(HxSwap.OUTER_HTML).settle(Duration.ofMillis(100)).show(ScrollEdge.TOP)));
```

- Nomes mantêm o prefixo: `hx-get` → `hxGet` (sem conflito com `Html5.*`).
- Textos visíveis (`hxConfirm`, `hxPrompt`) exigem `Text` (i18n).
- `hxVals(Map)` e `hxHeaders(Map)` serializam JSON.
- `hx-vars` não existe (obsoleto); use `hxVals`.

## 2. Lendo a requisição

```java
var hx = HtmxRequest.of(request);
if (hx.isHtmx() && !hx.boosted()) return fragmento();   // parcial
return paginaCompleta();
hx.target(); hx.trigger(); hx.currentUrl(); hx.prompt();  // Optional<String>
```

## 3. Escrevendo a resposta

```java
return HtmxResponse.of(WebResponse.html(HTTPStatus.UNPROCESSABLE_CONTENT, erros))
        .retarget("#errors")
        .reswap(HxSwap.INNER_HTML)
        .trigger(Map.of("toast", Map.of("msg", "Salvo")))
        .response();

HtmxResponse.of(WebResponse.noContent()).redirect("/login").response();  // navegação completa
HtmxResponse.of(resp).location("/orders").pushUrl("/orders").response(); // sem reload
```

## 4. Regras rápidas para agentes

1. Sempre `import static` de `Html5.*` e `Htmx.*` juntos; não crie
   `Attribute("hx-...")` à mão.
2. Endpoint que serve htmx e navegação normal: decida pelo
   `HtmxRequest.isHtmx()` (e `boosted()`).
3. Lembre do `Vary: HX-Request` se a resposta muda conforme o header e houver
   cache.
4. `hxOn`/`hxOnHtmx` recebem **JavaScript**: nunca concatene dado do usuário.
5. Validação com erro: 4xx + `retarget`/`reswap` (htmx não troca conteúdo em
   4xx/5xx por padrão sem configuração do cliente).
6. `HtmxResponse.response()` no fim — o wrapper não é `HTTPResponse`.

## 5. Checklist de revisão

- [ ] Fragmento vs página decidido por `HtmxRequest`.
- [ ] Nenhum dado de usuário em `hxOn`, seletores ou nomes de evento.
- [ ] Textos de confirmação via bundle (`Text`).
- [ ] Respostas de erro configuram alvo/troca explícitos.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 6. Representação

- Cada método devolve `br.commons.lang.html.core.Attribute(nome, valor)`; o escape
  do valor é responsabilidade do renderizador HTML.
- Atributos de flag (`hxPreserve`, `hxHistoryElt`, `hxDisable`) usam
  `flag(nome)` (atributo booleano, valor `null`). `hxValidate` = `"true"`,
  `hxHistoryOff` = `hx-history="false"`.
- `hxPartial(content...)` cria o elemento `<hx-partial>` (comando de swap no
  corpo da resposta), não um atributo.
- Enums implementam `Keyword.token()`: o texto exato do htmx
  (`HxSwap.OUTER_HTML.token()` = `outerHTML`).
- `SwapSpec` é imutável: cada modificador cria nova instância; `token()` junta
  estilo + modificadores com espaço. Durações viram `<ms>ms`.

## 7. Compostos

- `hxSync(selector, strategy)` → `"<selector>:<token>"`.
- `hxSwapOob(style, selector)` → `"<token>:<selector>"`.
- `hxOn(event, js)` → atributo `hx-on:<event>`; `hxOnHtmx(event, js)` →
  `hx-on::<event>` (eventos `htmx:`). O nome do atributo inclui `event` e
  passa pela validação de `Attribute` (espaço, `=`, `/`... →
  `IllegalArgumentException`).
- `hxParams(...)`, `hxParamsExcept(...)` (`not a,b`), `hxParamsAll` (`*`),
  `hxParamsNone` (`none`).

## 8. `HtmxRequest`

- Flags comparam exatamente `"true"`.
- Headers lidos case-insensitive via `HTTPMessage.header`.
- `HX-Current-URL`, `HX-Target` etc. são controlados pelo cliente: não use
  para autorização.

## 9. `HtmxResponse`

- Wrapper mutável: guarda a `HTTPResponse` e reatribui a cada `header(...)`.
- Chamadas de `trigger*` **acumulam** eventos por header. Só nomes → lista
  `"a, b"`; algum com detalhe (`Map`) → JSON via `Json.write`, evento sem
  detalhe vai como `""`.
- `redirect` = navegação completa; `location` = navegação AJAX;
  `refresh()` = reload. Também `replaceUrl`, `noPushUrl`/`noReplaceUrl`,
  `reselect`, `reswap(SwapSpec)`, `triggerAfterSwap`/`triggerAfterSettle`.
- CR/LF em valor (`pushUrl`/`location` vindos de dado) → `HTTPServerResponse.header`
  lança `IllegalArgumentException`: valide o dado antes de montar a resposta.

## 10. Extensão

| Necessidade               | Onde mexer                                          |
|---------------------------|-----------------------------------------------------|
| Novo atributo htmx        | método estático em `lang.Htmx` com javadoc + link  |
| Novo modificador de swap  | método em `SwapSpec` (`with("chave:valor")`)       |
| Header novo de resposta   | método em `HtmxResponse` via `header(...)`          |
| Extensões htmx            | `hxExt("nome")` + atributos próprios num pacote à parte |
