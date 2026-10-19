# br.commons.web — rotas, JSON e filtros sobre `br.commons.protocol.http`

Conveniências sobre a camada HTTP pura: roteador explícito, servidor web com
corpo por `MediaType` da rota, respostas JSON/HTML/SSE, `ProblemDetail` e
filtros prontos.

| Tipo / subpacote             | Papel                                                 |
|------------------------------|-------------------------------------------------------|
| `RouteHandler`               | `HTTPResponse handle(WebRequest)`                     |
| `WebResponse`                | `ok`, `created`, `json`, `html`, `redirect`, `problem`, `eventStream` |
| `ProblemDetail`              | Corpo de erro uniforme (RFC 7807 simplificado)        |
| `ErrorPage`                  | HTML de erro por status: `html(request, status)`      |
| `router/Router`              | Registro `método + template → handler`; `match`, `Allow` |
| `router/Route`               | Rota registrada (fonte do OpenAPI)                    |
| `router/RouteRule`           | Política de registro injetada no `Router`             |
| `server/WebServer`           | Custódia do `HTTPServer`; `register(WebResource)`; `Lifecycle` |
| `server/WebResource`         | Fatia de rotas: `routes(Router)`                      |
| `server/WebRequest`          | Request + path params + `body(Class)` pelo `consumes` |
| `server/Form`                | Corpo `x-www-form-urlencoded` (multivalor)            |
| `server/PreflightFilter`     | Responde `OPTIONS` com `Allow` (inserido pelo `WebServer`) |
| `filter/StaticFiles`         | Serve arquivos de classpath/diretório                 |
| `filter/RequestLoggingFilter`| Loga `método path -> status`                          |
| `filter/ErrorPages`          | Registro status → `ErrorPage` (inserido pelo `WebServer`) |

## 1. Recurso e rotas

```java
public final class OrderResource implements WebResource {
    public void routes(Router r) {
        r.get("/api/orders/{id}", MediaType.APPLICATION_JSON, OrderDto.class, this::find).getOrThrow();
        r.post("/api/orders", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON,
               NewOrder.class, OrderDto.class, this::create).getOrThrow();
    }
    HTTPResponse find(WebRequest req) {
        return req.pathParam("id").flatMap(service::find)
                  .map(WebResponse::ok)
                  .getOrElse(() -> WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND)));
    }
    HTTPResponse create(WebRequest req) {
        return switch (req.body(NewOrder.class)) {
            case Result.Success<NewOrder, String>(var o) -> WebResponse.created(service.create(o));
            case Result.Failure<NewOrder, String>(var e) -> WebResponse.problem(ProblemDetail.of(HTTPStatus.BAD_REQUEST, e));
        };
    }
}
```

- `register`/`get`/`post`... devolvem `Result<Route,String>` — ignorar não
  compila (`@CheckReturnValue`).
- `produces` obrigatório; `consumes` obrigatório em verbo com corpo.
- Rotas exigem autenticação por padrão; `postPublic` (login, cadastro) é a exceção.

## 2. Servidor

```java
var server = new WebServer(8080, new Router(), List.of(new RequestLoggingFilter(),
        new StaticFiles("/static/", "static", devDir, Duration.ofDays(7))),
        10_000_000, uploadLimits);
server.register(new OrderResource()); server.start().ifFailure(Logger::error);
```

`Lifecycle` (via `br.Application.attach(Module.of(server))`): `initialize()` = `start()` na thread não-daemon
`web-server` (segura a JVM); `terminate()` = `stop()`. `ErrorPages` + `PreflightFilter` vêm antes dos seus filtros.
Páginas de erro (`Result`, antes do start; §13): `defaultErrorPages()` (404/500),
`errorPage(HTTPStatus.NOT_FOUND, "/errors/404.html")` ou `errorPage(status, (req, st) -> html)`.

## 3. Corpo da requisição (`WebRequest.body`)

Tipo aceito por `consumes` da **rota** (nunca pelo `Content-Type` do cliente):
`APPLICATION_JSON` → qualquer (via `Json`); `TEXT_PLAIN` → `String`;
`URL_ENCODED` → `Form`; `MULTIPART_FORM_DATA` → `Multipart`.

## 4. Respostas

```java
WebResponse.ok(dto); WebResponse.created(dto); WebResponse.json(HTTPStatus.ACCEPTED, dto); WebResponse.noContent();
WebResponse.html(HTTPStatus.OK, pagina);        // página ou fragmento HTMX
WebResponse.redirect("/orders");                // 303 (post/redirect/get)
WebResponse.problem(ProblemDetail.of(HTTPStatus.CONFLICT, "detalhe", "order.conflict"));
WebResponse.eventStream(out -> ...);            // SSE
```

## 5. Regras rápidas para agentes

1. Cada fatia = um `WebResource`; sem scanning de classpath.
2. Trate o `Result` de cada registro (`getOrThrow` no bootstrap é aceitável).
3. Rota específica antes da genérica (`/x/new` antes de `/x/{id}`).
4. Erro ao cliente sempre `ProblemDetail`; nunca CPF/dado pessoal no detalhe.
5. Não registre `OPTIONS` (falha): o servidor responde sozinho.
6. Registre tudo antes de `start()`/`initialize()`.

## 6. Checklist de revisão

- [ ] `produces`/`consumes` corretos; `responseType` real (OpenAPI).
- [ ] Rotas públicas só via `postPublic`/`requiresAuthentication=false`.
- [ ] Ordem de registro não sombreia rotas.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Despacho (`WebDispatcher.handle`)

1. `router.match(method, path)`: varre rotas **na ordem de registro**; primeira
   que casa vence.
2. Casou → `handler.handle(new WebRequest(...))` → `produce`: em resposta 2xx
   com corpo/stream, força `Content-Type` = `route.produces()`. Não-2xx passa
   intacta.
3. Não casou: `allowHeader(path)` — se algum template casa com outro método →
   405 `ProblemDetail` + `Allow`; senão 404 (`routeNotFound`, i18n).
4. Exceção do handler → `HTTPServer.onFailure` = `WebResponse.internalServerError()`
   (500 com `ProblemDetail`).

## 8. Templates

- `/a/{id}/b` → regex `^/a/(?<id>[^/]+)/b$`; literais com `Pattern.quote`.
- Nome de parâmetro vira grupo nomeado: `[A-Za-z][A-Za-z0-9]*`. Nome
  inválido (`{order-id}`) ou repetido → `register` devolve `Failure`.
- Barras duplicadas/finais são ignoradas no template; o `path` da requisição
  já chega sem barra final.
- Parâmetro casa um segmento só; caminhos aninhados → filtro (como
  `StaticFiles`).
- `Allow` soma métodos de templates de mesma forma (`/x/{id}` + `/x/{key}`).

## 9. Concorrência

`Router.routes` é `ArrayList` e `allowed` um mapa comum, lidos sem lock.
`WebServer.start()` chama `Router.freeze()`: depois disso todo `register`
devolve `Failure`, e o router é só leitura (seguro entre threads). Registre
tudo antes do `start`.

## 10. `StaticFiles`

- Só `GET` sob o prefixo; `..` ou `\` no nome → 404. Diretório de
  desenvolvimento normalizado e checado com `startsWith` (anti traversal).
- Diretório vence o classpath; diretório/entrada de diretório → 404.
- `ETag` em toda resposta; `If-None-Match` igual → 304. Classpath: lido e
  hasheado (SHA-256) uma vez por nome, em cache. Diretório de
  desenvolvimento: tag = tamanho + mtime, 304 sem ler o arquivo.
- Sem `Range`; resposta 200 carrega o arquivo inteiro em memória. Arquivos
  grandes → servir por outro meio.
- `maxAge` zero → `no-cache`; adiciona `X-Content-Type-Options: nosniff`.
- Tipo desconhecido → `application/octet-stream` (ver mapa `TYPES`).

## 11. `RequestLoggingFilter`

Pula log quando o path **começa** com um prefixo ignorado (padrão
`/static/`, `/.well-known/`) ou **termina** com um sufixo ignorado (padrão
`.webmanifest`). Listas configuráveis no construtor.

## 12. `PreflightFilter`

Responde `OPTIONS` antes dos filtros do cliente (preflight não carrega
`Bearer`), usando o `Allow` pré-calculado; nenhum handler roda.

## 13. `ErrorPages`

Só vale se `Accept` contém `text/html` (navegação); API/`fetch`/HTMX seguem
com `ProblemDetail`. Resposta já HTML ou streaming passa intacta. Páginas
embutidas (`defaults()`): texto = `HTTPStatus.description()` (bundle
`http_status`), link "voltar" de `Errors`, `lang` = `L10N.locale()`
(propriedade `br.commons.locale`). `errorPage(status, "/recurso.html")` lê do classpath
uma vez; recurso ausente → `Failure`.

Primeiro filtro da cadeia (antes do `PreflightFilter`): cobre 404/405 do
dispatcher e respostas de filtros (`StaticFiles`). Troca só o corpo:
status, headers (ex.: `Allow`) e cookies originais ficam. Exceção na cadeia
com página 500 registrada → loga e devolve a página; sem página → relança
(`onFailure` do `HTTPServer`). Vazio por padrão; `freeze()` no `start()`.

## 14. Dependências entre subpacotes

- Raiz (`RouteHandler`, `WebResponse`, `ProblemDetail`, `ErrorPage`) é o
  vocabulário comum; `filter` só vê a raiz e `protocol.http` — nunca
  `server`/`router` (`WebArchitectureTest`), porque o `WebServer` instala
  `ErrorPages`. Filtro que precisa do roteador (`PreflightFilter`) mora em
  `server`.
- Ainda há ciclo raiz ↔ `server` ↔ `router`: `RouteHandler` recebe
  `WebRequest` (`server`), que carrega a `Route` (`router`), que guarda o
  `RouteHandler`.

## 15. Observações

- `Form.parse` lança `IllegalArgumentException` em percent-encoding inválido;
  `WebRequest.body(Form.class)` converte em `Failure`.
- JSON usa `br.commons.lang.Json` (`Json.from(...).as(type)`).

## 16. Extensão

| Necessidade              | Onde mexer                                          |
|--------------------------|-----------------------------------------------------|
| Novo formato de corpo    | `WebRequest.body` (`case` no `switch`)             |
| Regras de rota (ex.: só `/api`) | `RouteRule` no construtor do `Router`        |
| Prioridade de rota       | ordenar literais antes de parâmetros em `match`    |
