# br.commons.logger — infraestrutura do Logger

A fachada é `br.commons.Logger` (raiz). Este pacote tem o que ela usa: níveis,
filtro por pacote, registro, MDC, canais de saída, forwarders e ponte JUL.

| Tipo / subpacote       | Papel                                                    |
|------------------------|----------------------------------------------------------|
| `LogLevel`             | `OFF < VERBOSE < TRACE < DEBUG < INFO < WARN < ERROR < FATAL` |
| `LogFilter`            | Nível por pacote, hierárquico, com cache por classe      |
| `LogRecord`            | `timestamp`, `level`, `threadName`, `Supplier` da mensagem, chamador |
| `MDC`                  | Contexto por thread (`[k=v, ...]` prefixado na linha)    |
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
método. `fatal` **não** encerra a JVM (adaptação JUDAS, spec §2.4) — quem loga
FATAL lança em seguida.

## 9. `LogFilter`

- `ConcurrentSkipListMap<pacote, nível>` + cache `classe → nível` efetivo.
- Mudar nível global ou por pacote invalida o cache.
- Busca hierárquica por segmentos (`a.b.C` → `a.b` → `a`): aceita também classe exata; mais específico vence; sem match → global.

## 10. Canais de arquivo

`FileChannel(path)`:
- Thread daemon `FileChannel[nome]`, drena fila a cada 100 ms.
- Abre com `TRUNCATE_EXISTING`: **apaga** o arquivo ao iniciar.
- Laço infinito; o `finally` de flush só roda em erro. Mensagens nos últimos
  ~100 ms antes do fim da JVM podem se perder (daemon não tem shutdown hook).
- Falha ao abrir → `System.err`, canal vira no-op silencioso.

`RollingFileChannel()`:
- Diretório = `Platform.fileSystem().jvmFS()`; virtual thread com
  `RollingFileWriter`, rotação diária (`MILLIS_IN_DAY`).
- Laço roda **enquanto `RT.running`**. Se o canal for criado com
  `RT.running == false`, o writer termina na hora e a fila cresce sem
  consumo (vazamento). Ligue `RT.running` antes de criar o canal.
- Mesma perda possível de mensagens finais.

## 11. Detalhes diversos

- `LogLevel.fromSlf4jLevel`: `FATAL`/`VERBOSE`/desconhecido viram `INFO`.
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
| Flush no shutdown     | shutdown hook drenando filas de `FileChannel`/rolling   |
| Nível novo            | `LogLevel` + forwarder na cadeia + fachada `Logger`     |
| SLF4J                 | bridge análoga ao JUL usando `*WithCaller`              |
