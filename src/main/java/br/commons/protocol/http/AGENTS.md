# br.commons.protocol.http — camada HTTP pura

Modelo da mensagem na rede e servidor sobre `com.sun.net.httpserver`, sem
roteamento, JSON nem formato de erro (isso é `br.commons.web`, que depende
deste pacote — nunca o contrário). Também não conhece `br.commons.lang`
(HTML, htmx, JSON, YAML) nem `br.commons.serializer`; `HTTPArchitectureTest`
garante.

Referência: https://developer.mozilla.org/en-US/docs/Web/HTTP

| Tipo / subpacote                   | Papel                                              |
|------------------------------------|----------------------------------------------------|
| `message/HTTPMessage`              | Headers (nomes minúsculos) + body em bytes; `X-REQUEST-ID` |
| `message/HTTPRequest`/`HTTPResponse` | Contratos de requisição e resposta              |
| `message/HTTPMethod`               | `GET, POST, PUT, PATCH, DELETE, OPTIONS`           |
| `HTTPStatus`                       | Constantes 1xx–5xx; `custom(code, ...)` validado; `description()` via i18n |
| `MediaType`                        | Enum de MIME; `contentType()` com charset (multipart sem); `fromContentType` |
| `Cookie`                           | `Set-Cookie`; `session(...)`, `expired(...)`       |
| `HTTPSession`                      | Atributos por requisição, chave = request id       |
| `Multipart`                        | Campos em memória + arquivos temporários           |
| `server/HTTPServer`                | Servidor, cadeia de filtros, limites (o `Lifecycle` é o `WebServer`) |
| `server/HTTPFilter`/`HTTPHandler`  | Elo da cadeia / fim da cadeia                      |
| `server/MultipartParser`           | Parser streaming RFC 7578                          |
| `server/message/HTTPServer*`       | Implementações concretas de request/response       |

## 1. Servidor

```java
HTTPHandler app = req -> HTTPServerResponse.status(HTTPStatus.OK, "oi".getBytes(UTF_8))
                                          .mediaType(MediaType.TEXT_PLAIN);
HTTPFilter log = (req, next) -> { var r = next.handle(req); Logger.info("%s", r.status()); return r; };

var server = new HTTPServer(8080, List.of(log), app,
        failure -> HTTPServerResponse.status(HTTPStatus.INTERNAL_SERVER_ERROR),
        10 * 1024 * 1024,                                         // maxBodySize → 413
        new MultipartParser.Limits(uploadDir, 50_000_000, 60_000_000));
server.start().ifFailure(Logger::error);
server.port();                       // porta real (útil com 0)
server.stop(Duration.ofSeconds(5));  // stop() = sem espera
```

Filtros executam na ordem da lista; cada um decide chamar `next.handle`.

## 2. Requisição

```java
req.method(); req.path();            // path sem barra final
req.parameters().get("q");           // query decodificada; repetido = último
req.header("Content-Type");          // case-insensitive
req.body();                          // bytes (vazio em multipart)
req.cookie("SESSION");
req.multipart().field("nome"); req.multipart().files("anexo");
req.attribute("user", u);            // passa dados entre filtros e handler
```

## 3. Resposta

```java
HTTPServerResponse.status(HTTPStatus.CREATED, json).mediaType(MediaType.APPLICATION_JSON);
HTTPServerResponse.noContent();
HTTPServerResponse.stream(out -> sse(out)).mediaType(MediaType.TEXT_EVENT_STREAM);
resp.header("Cache-Control", "no-store").cookie(Cookie.session("SID", token));
resp.cookie(Cookie.expired("SID"));
```

## 4. Status e sessão

```java
HTTPStatus.custom(299, "X", "desc");                 // Result: falha fora de 100–599
try (var s = HTTPSession.open(req.requestId())) {    // quem abre fecha
    s.attribute("tenant", t);
    HTTPSession.current().flatMap(x -> x.attribute("tenant", Tenant.class));
}
```

## 5. Regras rápidas para agentes

1. Nada de JSON/rota/erro de domínio aqui; vá para `br.commons.web`.
2. Header sempre por `header(nome)` (minúsculo internamente).
3. Body grande → `maxBodySize`; upload → `Limits` de multipart.
4. `HTTPSession` sempre em try-with-resources.
5. Arquivos de upload morrem com a requisição: copie se precisar mantê-los.
6. Status novo → `HTTPStatus.custom`, nunca construtor (privado).

## 6. Checklist de revisão

- [ ] Servidor criado com `maxBodySize` finito em produção.
- [ ] `onFailure` não vaza stack trace ao cliente.
- [ ] Sessão fechada; uploads movidos se persistidos.
- [ ] Nenhuma dependência de `br.commons.web`/`lang`/`serializer` dentro de `protocol`.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Ciclo de uma requisição (`HTTPServer.dispatch`)

1. `requestId` = UUID gerado pelo servidor, sempre; vai para `MDC`
   (`requestId`). O `X-REQUEST-ID` do cliente, se casar
   `[A-Za-z0-9._-]{1,64}`, vai para `MDC` (`clientRequestId`) e é ecoado na
   resposta; senão a resposta ecoa o id do servidor.
2. Método fora de `HTTPMethod` (HEAD, TRACE...) → 405 sem passar na cadeia.
3. Query parseada com `URLDecoder` → falha vira 400.
4. Multipart (se `Limits` configurado e `Content-Type` aceito) → streaming
   para disco; `TooLarge` → 413; malformado → 400. Senão, body em memória
   lendo até `limit + 1` byte (sem overflow em `Long.MAX_VALUE`) → 413.
5. Cadeia composta uma vez no construtor (`compose`, de trás para frente).
6. Qualquer `Throwable` (inclusive `Error`) → log ERROR + `onFailure`;
   `writeSafely` descarta headers da resposta original se ainda não enviados.
7. `finally`: apaga arquivos de multipart, fecha exchange, `MDC.pop`.

Executor: uma virtual thread por requisição. `stop(grace)` usa segundos
inteiros arredondados para cima (`500ms` → 1 s).

## 8. Defaults perigosos

- Construtores curtos (`HTTPServer` e `WebServer`) usam
  `HTTPServer.defaultMaxBodySize()`: 1 MiB, ou a system property
  `br.commons.http.maxBodySize` (bytes; inválido → WARN e 1 MiB; chave mantida
  do pacote antigo `br.commons.http` por compatibilidade). Sem limite
  só com `Long.MAX_VALUE` explícito. Sem multipart (lido em memória).
- `header(nome, valor)` com CR/LF → `IllegalArgumentException`; `Cookie`
  recusa CR, LF e `;` em nome, valor, path e sameSite.
- `onFailure` padrão: 500 sem corpo.

## 9. Segurança — correlação × identidade

- `requestId()` (chave de `HTTPSession` e do MDC) é sempre do servidor:
  duas requisições com o mesmo `X-REQUEST-ID` nunca compartilham sessão.
- `clientRequestId()` / `HTTPServerRequest.clientRequestId(header)`: id do
  cliente validado (`[A-Za-z0-9._-]{1,64}`) ou vazio — só correlação; valor
  inválido nunca chega ao log.

## 10. Multipart

- `MultipartParser.Limits(uploadDir, maxFileSize, maxRequestSize)`.
- Arquivo acima de `maxFileSize`: `UploadedFile.tooLarge() == true`, `path`
  guarda só os bytes até o limite e `size` reporta o total real — rejeite o
  upload quando `tooLarge`.
- Campo texto acima de `MAX_FIELD_SIZE` → parte malformada (400).
- Request acima de `maxRequestSize` → `TooLarge`, temporários já escritos são
  apagados.
- `Multipart.delete()` idempotente.

## 11. Modelo

- `HTTPServerRequest`: id, parâmetros e headers imutáveis; só `attributes`
  muda ao longo da cadeia. Header duplicado só por caixa: o primeiro vence.
- `HTTPServerResponse`: corpo fixo **ou** streaming; `streamWriter()` devolve
  `Failure` se o corpo é fixo. Cookies acumulam (não substituem como header).
- `Cookie.session`: `HttpOnly; Secure; SameSite=Strict; Path=/`, sem
  `Max-Age` — exige HTTPS (em `http://localhost` alguns navegadores ignoram).

## 12. Extensão

| Necessidade              | Onde mexer                                          |
|--------------------------|-----------------------------------------------------|
| HEAD automático          | `HTTPServer.method` + resposta sem corpo            |
| Formato do id do cliente | `HTTPServerRequest.CLIENT_REQUEST_ID_FORMAT`        |
| Compressão               | `HTTPFilter` envolvendo body/stream                 |
| HTTPS                    | trocar `HttpServer` por `HttpsServer` em `start`    |
