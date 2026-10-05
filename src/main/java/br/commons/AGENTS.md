# br.commons — núcleo utilitário do br-engine

Classes estáticas e tipos base usados por todos os outros pacotes. Este
arquivo cobre **só** as classes da raiz; cada subpacote tem seu AGENTS.md.

| Tipo       | Papel                                                            |
|------------|------------------------------------------------------------------|
| `Result`   | `Success`/`Failure` selado — erro como valor                     |
| `Logger`   | Fachada de log estática (níveis, filtro por pacote, canais)      |
| `Meta`     | Reflexão: instanciar, `fqn`, `assignable`, pilha, campos, `exit` |
| `Strings`  | Constantes e helpers de texto (pad, leitura, sha256, split)      |
| `Threads`  | `locked`, virtual threads, daemon, sleep, interrupt              |
| `Platform` | SO atual (`CURRENT`) e serviços: `fileSystem`, `terminal`, `network` |
| `RT`       | Estado de runtime: `running`, prefixos de pacotes de infraestrutura |
| `Parser<T>`| Contrato `parseable(raw)` + `parse(raw)`                         |
| `Tuple`    | Par `name`/`value` (string)                                      |

## 1. `Result`

```java
Result<User, String> r = repo.find(id);                 // infra falha em String
Result<User, BusinessError> d = r.mapFailure(e -> new BusinessError.NotFound("user.not_found", id));
r.map(User::name).getOrElse("anônimo");
r.flatMap(u -> outro(u)).ifFailure(Logger::warn);
switch (r) {
    case Result.Success<User, String>(var u) -> ...;
    case Result.Failure<User, String>(var e) -> ...;
}
```

- `Result.success()` → `Result<Void, E>` com valor `null`.
- `get()` em `Failure` loga FATAL e lança; `getOrThrow()` só lança.
- Prefira `switch`/`map`/`getOrElse`; `get()` só onde falha é bug.

## 2. `Logger`

```java
Logger.info("Servidor em %s", port);                    // String.format
Logger.debug(() -> "caro: %s".formatted(calc()));         // lazy
Logger.debug("x=%s", Logger.lazy(() -> calc()));         // argumento lazy
if (Logger.enabled(LogLevel.TRACE)) { ... }              // gate manual
Logger.level(LogLevel.DEBUG);                            // global
Logger.level("br.commons.persistence", LogLevel.TRACE);  // por pacote
```

Mensagem de terceiros (com `%` possível): use a sobrecarga `Supplier`, nunca a
varargs. Detalhes em `br/commons/logger/AGENTS.md`.

## 3. `Meta`

```java
Meta.fqn(obj); Meta.fqn(Classe.class);   // nome qualificado
Meta.instance(Classe.class);             // construtor público sem args
Meta.assignable(sub, Base.class);
Meta.close(closeable);                   // fecha logando falha
```

`instance` e `cast` chamam `exit(99)` (encerra a JVM) se falharem.

## 4. `Strings`, `Threads`, `Platform`

```java
Strings.lpad(7, 3, '0');                 // "007"
Strings.read(path);                      // UTF-8
Strings.read(bytes);                     // UTF-8, fallback ISO-8859-1
Strings.sha256(texto);
Threads.locked(lock, () -> ...);         // Runnable ou Callable<T>
Threads.virtual(() -> tarefa());         // virtual thread
Threads.daemon("nome", runnable);
Platform.CURRENT;                        // WINDOWS, LINUX, MAC, UNKNOWN
Platform.fileSystem();                   // implementação do SO (ver platform/)
```

## 5. Regras rápidas para agentes

1. Erro esperado → `Result`; exceção só para bug ou borda de framework.
2. Nunca `System.out`/`printStackTrace`: use `Logger`.
3. Argumento caro em log → `Supplier`/`lazy`/`enabled`.
4. Reflexão → `Meta`, não `Class.forName` espalhado.
5. Lock → `Threads.locked` (garante `unlock` no `finally`).
6. Nada de `Thread.sleep` com `try/catch` manual: `Threads.sleep`.
7. Lembre que `Meta.instance`/`cast` encerram a JVM em falha — não use para
   instanciar algo opcional.

## 6. Checklist de revisão

- [ ] Nenhum `Result.get()` em caminho onde falha é esperada.
- [ ] Logs com texto externo usam sobrecarga `Supplier`.
- [ ] Locks via `Threads.locked`.
- [ ] Classe nova no core é `@NullMarked` e utilitária com construtor privado.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. `Result` em detalhe

- Selado (`permits Success, Failure`): `switch` exaustivo sem `default`.
- `Success.value` é `@Nullable`; `Failure.error` não.
- Fábricas `failure(String)`, `failure(BusinessError)`, `failure(Throwable)`
  fixam `E`; para outro tipo de erro use `new Result.Failure<>(e)`.
- `recover` converte falha em sucesso; `mapFailure` traduz o erro entre
  camadas (infra `String` → domínio `BusinessError`).
- `ifSuccess`/`ifFailure` devolvem `this` (encadeáveis).

## 8. `Logger` — gate e custo

- Nível efetivo = override por pacote (`LogFilter`, hierárquico) ou global.
  `OFF` tem ordinal 0 e é tratado à parte.
- Sem filtros por pacote: gate só compara nível global (barato). Com filtros:
  resolve chamador via `StackWalker` (caro). Em hot path use
  `enabled(level, CALLER)` com `CALLER` constante.
- Varargs são avaliados **antes** da chamada: `debug("%s", caro())` paga
  `caro()` mesmo desligado.
- Frames de `RT.packages` são ignorados ao achar o chamador.
- No `<clinit>`, elege JBoss LogManager para JUL se presente no classpath e
  `java.util.logging.manager` não definido (carrega sem inicializar).

## 9. `Meta`

- `instance(type, args...)` → `Result<T, String>`: escolhe o construtor
  público que aceita os `args` (primitivo casa com wrapper; `null` com
  referência). Nenhum, ambíguo ou construtor que lança → `Failure`.
  Armadilha: um único `String` cai em `instance(type, property)` (nome de
  classe) — passe `(Object) texto`.
- `exit(...)` loga ERROR e chama `System.exit`. Em teste isso mata o fork.
- `fqn(Object)` de lambda (classe sintética) devolve `classe.metodo` do frame
  chamador; `method(lambda)` resolve rótulo para `Execution`.
- `stackFrame(...)` filtra prefixos de `RT.packages`; `userMainClassName()` e
  `mainPackageName()` acham o ponto de entrada do usuário.
- SO corrente: use `Platform.CURRENT`.

## 10. `Strings`

- `isNumeric("")`, `isUpperCase("")`, `isLowerCase("")` → `false`.
- `read(byte[])` sem detecção de charset: UTF-8 estrito, senão ISO-8859-1.
- `bytes/shorts/ints/longs/floats/doubles(String)` parseiam listas a partir de
  texto (aceitam `null`).

## 11. `Threads`

- `locked(lock, Callable)` converte checked exception em
  `IllegalStateException`; `RuntimeException` passa intacta.
- `virtual` usa `newVirtualThreadPerTaskExecutor` estático, nunca fechado.
- `sleep`/`wait` terminam cedo na interrupção e **restauram** a flag: laços
  devem testar `Thread.currentThread().isInterrupted()`.
- `interrupt(thread)` dispara a interrupção numa virtual thread (assíncrono).
- `result(future)` devolve `null` em falha (loga ERROR).

## 12. `Platform` e `RT`

- `Platform.CURRENT` detectado por `os.name` no `<clinit>`.
- `fileSystem()`/`terminal()`/`network()`: holder idiom, instância única
  thread-safe. SO não suportado → `UnsupportedOperationException`.
- `RT.running`: flag volátil de ciclo de vida. `RT.packages`:
  `CopyOnWriteArrayList` mutável — registre prefixos de infraestrutura de
  outros módulos no bootstrap.
