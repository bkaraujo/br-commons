# br.commons.logger — infraestrutura do Logger

A fachada é `br.commons.Logger` (raiz). Este pacote tem o que ela usa: níveis,
filtro por pacote, registro, MDC, canais de saída, forwarders e ponte JUL.

| Tipo / subpacote       | Papel                                                    |
|------------------------|----------------------------------------------------------|
| `LogLevel`             | `OFF < VERBOSE < TRACE < DEBUG < INFO < WARN < ERROR < FATAL` |
| `LogFilter`            | Nível por pacote, hierárquico, com cache por classe      |
| `LogRecord`            | `timestamp`, `level`, `threadName`, `messageSupplier`, `callerClass` |
| `MDC`                  | Contexto por thread: `push`/`get`/`pop`/`prefix` (`[k=v, ...]`) |
| `LogChannel`           | Destino: `write(String linha)`                           |
| `LogForwarder`         | Métodos por nível recebendo `Supplier<String>`           |
| `channel/`             | `ConsoleChannel`, `FileChannel`, `RollingFileChannel`, `Ansi` |
| `forwarder/`           | Cadeia selada `Fatal ← Error ← Warn ← Info ← Debug ← Trace ← Verbose` |
| `bridge/`              | `JULBridgeHandler`: `java.util.logging` → `Logger`       |

## 1. Configurar nível

```java
Logger.level(LogLevel.INFO);                               // global
Logger.level("br.commons.persistence", LogLevel.TRACE);    // pacote e filhos
Logger.level("br.commons.persistence.jdbc.pool");          // nível efetivo
Logger.removePackageLevel("br.commons.persistence");
Logger.clearPackageFilters();
```

`OFF` silencia tudo (tratado à parte, não por ordinal).

Mensagem usa placeholder `String.format` (`%s`, `%d`), **não** `{}`:

```java
Logger.info("Pedido %s pago em %d ms", id, ms);
Logger.debug("Custo: %s", Logger.lazy(() -> caro()));     // avaliado só se emitir
if (Logger.enabled(LogLevel.DEBUG, CALLER)) { ... }       // CALLER = Meta.fqn(X.class): gate sem StackWalker
Logger.stackTrace(LogLevel.DEBUG);                         // pilha atual no nível
```

## 2. Canais

```java
Logger.channel(new ConsoleChannel());                      // substitui todos
Logger.addChannel(new FileChannel(Path.of("app.log")));     // adiciona
Logger.addChannel(new RollingFileChannel());               // arquivo por dia
```

Canais de arquivo removem sequências ANSI (`Ansi.writeStripped`); console
mantém cores.

## 3. MDC

```java
MDC.push("requestId", id);
try { ... Logger.info("processando"); }    // "[requestId=...] processando"
finally { MDC.pop("requestId"); }
```

Sempre `pop` no `finally`: o mapa é `ThreadLocal` e vaza entre requisições em
pool de threads.

## 4. Ponte JUL

```java
JULBridgeHandler.install();   // remove handlers do root JUL e redireciona
```

Já instalada no init estático do `Logger` (após eleger o `LogManager` do
JBoss, se estiver no classpath e `java.util.logging.manager` não definido).

Mapeamento: `≤FINEST` TRACE, `≤FINE` DEBUG, `≤INFO` INFO, `≤WARNING` WARN,
acima ERROR. Falha ao encaminhar vai para `stderr`, nunca propaga.

## 5. Regras rápidas para agentes

1. Use a fachada `br.commons.Logger`; não instancie forwarders.
2. Canal novo implementa `LogChannel.write` **sem bloquear** (enfileire).
3. Canal não pode lançar: o log é chamado de qualquer lugar.
4. Mensagem de terceiros → sobrecarga `Supplier` (evita `%` como formato).
5. `MDC.push` sempre pareado com `pop` em `finally`.
6. `LogLevel.ordinal` (campo) = `ordinal()` do enum; ordem dos constantes é
   contrato — não reordene.

## 6. Checklist de revisão

- [ ] Nenhum canal faz I/O síncrono no thread do chamador (exceto console).
- [ ] `MDC` limpo no fim da requisição/tarefa.
- [ ] Filtros por pacote só onde necessário (custo de stack walk).

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Fluxo de uma chamada

1. `Logger.debug(fmt, args)` → gate `enabled(DEBUG)`:
   - sem filtros de pacote: compara com nível global;
   - com filtros: resolve chamador (`StackWalker`, pulando `RT.packages`) e
     consulta `LogFilter.getEffectiveLevel(classe)` (memoizado).
2. Passou → `forwarder.debug(() -> fmt.formatted(Meta.evaluate(args)))`.
   `Meta.evaluate` resolve argumentos `Supplier` (`Logger.lazy`).
3. `FatalForwarder.forward(record)`: resolve frame (chamador externo da ponte
   → `record.callerClass` → pilha), formata a linha num `StringBuilder`
   `ThreadLocal` (dígitos via tabelas `HORIZONTAL`/`VERTICAL`, sem divisão) e
   chama `write` em cada canal.

Único forwarder: `VerboseForwarder` (emite tudo). O filtro de nível é **só**
o gate; forwarders nunca filtram.

## 8. Forwarders

Hierarquia selada em cadeia: cada classe adiciona um nível e é `final` no
método. `fatal` **não** encerra a JVM (por decisão) — quem loga FATAL lança
em seguida. `Logger.fatal` anexa a pilha do chamador à mensagem.

## 9. `LogFilter`

- `ConcurrentSkipListMap<pacote, nível>` + cache `classe → nível` efetivo.
- Mudar nível global ou por pacote invalida o cache.
- Busca hierárquica por segmentos (`a.b.C` → `a.b` → `a`): aceita também classe exata; mais específico vence; sem match → global.

## 10. Canais de arquivo

`FileChannel(path[, Mode])`:
- Thread daemon `FileChannel[nome]`, drena fila a cada 100 ms.
- `Mode.APPEND` (padrão) acrescenta; `Mode.TRUNCATE` começa vazio.
- `close()` e shutdown hook: param o laço, drenam a fila e fecham (espera
  até 2 s). Mensagens após o `close` não são gravadas.
- Falha ao abrir → `System.err`, canal vira no-op silencioso.

`RollingFileChannel([dir])`:
- Diretório padrão = `Platform.fileSystem().jvmFS()`; virtual thread com
  `RollingFileWriter`, um arquivo `yyyy-MM-dd.log` por dia (`MILLIS_IN_DAY`, dia UTC).
- Laço com flag própria (independe de `RT.running`); `close()` e shutdown
  hook drenam a fila e fecham o arquivo (espera até 2 s).

## 11. Detalhes diversos

- `LogLevel.fromSlf4jLevel`: mapeia todos os níveis; só desconhecido vira `INFO`.
- Nível por system property: `br.commons.log.level` (global) e
  `br.commons.log.level.<pacote>`. Chaves da biblioteca sempre com prefixo
  `br.commons.`.
- `LogRecord.userMessage()` avalia o `Supplier` a cada chamada (não memoiza):
  canais múltiplos recebem a linha já formatada, então avalia uma vez por log.
- `Logger.externalCaller()` (ThreadLocal) permite à ponte/SLF4J informar o
  chamador real; `*WithCaller` são para bridges.
- `ConsoleChannel` usa `System.out.printf` — síncrono, contende em
  `System.out` sob carga.

## 12. Extensão

| Necessidade           | Onde mexer                                              |
|-----------------------|---------------------------------------------------------|
| Saída JSON            | novo `LogChannel` ou formatação alternativa em `forward` |
| Nível novo            | `LogLevel` + forwarder na cadeia + fachada `Logger`     |
| SLF4J                 | bridge análoga ao JUL usando `*WithCaller`              |
