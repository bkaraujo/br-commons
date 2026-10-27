# br-engine

Biblioteca Java 25 sem framework para aplicações web e de dados: HTTP, rotas,
HTML tipado, htmx, JDBC com transações e migrações, cache off-heap, i18n,
logging e serialização — com **erro como valor** (`Result`) e nulidade checada
em compilação (JSpecify + NullAway).

- **Artefato:** `br.engine:core:1.0.0-SNAPSHOT`
- **Pacote raiz:** `br.commons`
- **Java:** 25 (FFM API para memória off-heap e chamadas nativas)

## Princípios

- **Erro como valor.** Falha esperada volta como `Result.Failure`; exceção só
  para bug de programação ou borda de framework.
- **Sem framework, sem mágica.** Nada de scan de classpath ou anotações de
  injeção: rotas, recursos e dependências são registrados explicitamente.
- **Nulidade no tipo.** Todo código é `@NullMarked`; NullAway falha o build.
- **Configuração com prefixo `br.commons.`.** Ex.: `br.commons.log.level`,
  `br.commons.http.maxBodySize`.

## Pacotes

| Pacote | Conteúdo |
|---|---|
| `br.commons` | `Result`, `Logger`, `Meta`, `Strings`, `Threads`, `Platform` |
| `business` | `BusinessError` (chave de tradução + args), `BusinessException` e `BusinessEvent` |
| `cache` | Cache off-heap particionado (`Cache`, `CacheRegion`, `CacheServer`, `CacheWorker`) |
| `cdi` | `Context`: registro global de singletons |
| `chrono` | Tempo e datas (`Time`, `Dates`) |
| `debug` | Cronometragem condicional (`Execution`) |
| `lang` | Linguagens, uma fachada cada: `Html4`/`Html5` (DSL HTML tipada, com escape e formulários), `Htmx` (atributos `hx-*` e headers htmx), `Json`/`Yaml` (formatos do `serializer`) |
| `locale` | `i18n` (textos por interface + bundle, formatação por locale) e `l10n` (locales suportados e bundles da lib) |
| `logger` | Canais (console, arquivo, arquivo diário), MDC, ponte JUL |
| `message` | `MessageBus`: mensagens in-process |
| `persistence` | `DataSource` com pool, transações, `JDBCRepository`, `Migrator`, `Storage` |
| `platform` | SO, terminal e rede via FFM; `NativeCache` (memória off-heap) |
| `protocol` | `http`: servidor HTTP puro (`HTTPServer`), cookies, multipart, sessão por requisição |
| `serializer` | Modelo de documento comum (`Serializer`) lido de JSON/YAML (`lang`) |
| `text` | Normalização de texto |
| `web` | Rotas, JSON, `WebServer` e filtros sobre `protocol.http` |

Cada pacote tem um `AGENTS.md` com uso, regras, checklist de revisão e
detalhes de implementação — é a documentação de referência.

## Exemplo rápido

```java
public final class OrderResource implements WebResource {
    public void routes(Router r) {
        r.register(Route.get("/api/orders/{id}", MediaType.APPLICATION_JSON, OrderDto.class, true, this::find)).getOrThrow();
    }

    HTTPResponse find(WebRequest req) {
        return req.pathParam("id").flatMap(service::find)
                .map(WebResponse::ok)
                .getOrElse(() -> WebResponse.problem(ProblemDetail.of(HTTPStatus.NOT_FOUND)));
    }
}

var server = new WebServer(8080, List.of(new RequestLoggingFilter()));
server.register(new OrderResource());   // registre tudo antes do start
server.start().ifFailure(Logger::error);
```

Transação com efeito pós-commit:

```java
Result<Order, BusinessError> saved = dataSource.inTransaction(() -> {
    dataSource.execute("INSERT INTO orders (id, total) VALUES (?, ?)", JDBCParameter.of(id, total));
    dataSource.afterCommit(() -> bus.publish(new OrderPlaced(id)));   // só depois do commit
    return Result.success(order);
});
```

## Build

Requisitos: JDK 25 e Maven 3.9+.

```sh
mvn test      # compila (Error Prone + NullAway) e roda os testes
mvn verify    # + PMD, detecção de código duplicado (CPD) e relatório JaCoCo
```

Os testes de banco usam H2 em memória; testes com PostgreSQL usam
Testcontainers (exigem Docker).

### Aplicações embarcadas

Aplicações completas em `br.bundled`, no jar e sob os mesmos quality gates da biblioteca:

| Exemplo | Classe principal |
|---|---|
| Lista de tarefas com jQuery sobre API JSON (porta 5000) | `br.bundled.tasks.jquery.TasksApp` |
| Lista de tarefas com htmx (porta 5003) | `br.bundled.tasks.htmx.TasksApp` |
| Controle de horas com htmx (porta 5001) | `br.bundled.tracker.htmx.TrackerApp` |
| Controle de horas com jQuery sobre API JSON (porta 5002) | `br.bundled.tracker.jquery.TrackerApp` |

```sh
mvn -Pexamples compile exec:java                                    # TasksApp na porta 5000
mvn -Pexamples compile exec:java \
    -Dexamples.main=br.bundled.tracker.htmx.TrackerApp -Dexec.args=8080
```

Cada aplicação segue `br.bundled.<aplicação>.[shared, htmx, jquery]` (estáticos em `br/bundled/<aplicação>/static`): `shared` tem o domínio
(modelos, stores, campos e validação, sem HTTP nem HTML); `htmx` renderiza no servidor; `jquery` renderiza no
navegador sobre uma API JSON.

## Configuração

System properties lidas pela biblioteca:

| Propriedade | Efeito | Padrão |
|---|---|---|
| `br.commons.log.level` | Nível global de log | `INFO` |
| `br.commons.log.level.<pacote>` | Nível de um pacote | — |
| `br.commons.http.maxBodySize` | Limite de corpo (bytes) dos construtores sem limite explícito | 1 MiB |

## Estrutura do repositório

```
src/main/java       biblioteca (br.commons.*) e aplicações embarcadas (br.bundled.*, perfil Maven "examples")
src/test/java       testes JUnit 5, ArchUnit e de regressão
config/pmd          regras PMD
.agents/            planos e notas de trabalho
```
