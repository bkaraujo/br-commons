# br.commons.platform — SO, terminal, rede e memória off-heap

Abstrações do sistema operacional com implementação por SO (Linux e Windows,
este via FFM — Foreign Function & Memory) e o cache off-heap `NativeCache`.
Acesso aos serviços pela fachada `br.commons.Platform` (pacote raiz).

| Tipo / subpacote             | Papel                                              |
|------------------------------|----------------------------------------------------|
| `OS`                         | `WINDOWS`, `LINUX`, `MAC`, `UNKNOWN`               |
| `FileSystem`                 | `jvmFS()` (diretório de início), `userFS()` (home) |
| `Network`                    | `isPortInUse(port)` — só sockets em LISTEN         |
| `Terminal`                   | Dimensão, modo raw, `render` de buffer de células  |
| `NativeCache<K>`             | Arena off-heap indexada por chave; API `Result`    |
| `terminal/TerminalColor`     | Cores ANSI; `ofRGB`, `ofHex`, `colorize`           |
| `provider/linux/`            | `LNXFileSystem`, `LNXNetwork` (`/proc/net/tcp*`), `LNXTerminal` |
| `provider/windows/`          | `WIN*` + bindings FFM (`Kernel32`, `User32`, `Shell32`, `Iphlpapi`) |
| `provider/agnostic/`         | `AbstractTerminal` (render ANSI), `Network` estático |

## 1. Serviços

```java
Platform.CURRENT;                          // OS detectado
Path base = Platform.fileSystem().jvmFS(); // diretório onde a JVM iniciou
Path home = Platform.fileSystem().userFS();
boolean ocupada = Platform.isPortInUse(8080);
Terminal t = Platform.terminal();
Dimension d = t.dimension();               // (0,0) se não for TTY
```

## 2. `NativeCache`

```java
try (var cache = new NativeCache<UUID>()) {
    cache.put(id, 24).ifSuccess(seg -> {
        NativeCache.writeUuid(seg, 0, id);
        NativeCache.writeMoney(seg, 16, new BigDecimal("12.34"));   // centavos
    });
    MemorySegment s = cache.segment(id);        // hot path: null em miss/fechado
    cache.get(id);                              // Result (ERROR_MISS / ERROR_CLOSED)
    cache.forEach((k, seg) -> ...);             // ordem de inserção
    cache.remove(id); cache.keys(); cache.count();
}
```

Codecs estáticos (offset em bytes, acesso `*_UNALIGNED`):

| Codec                         | Bytes        | `null` vira                     |
|-------------------------------|--------------|---------------------------------|
| `writeUuid` / `readUuid`      | 16           | 0/0 (lido como `null`)          |
| `writeString(cap)`/`readString` | 4 + até cap-4 | tamanho `-1`                  |
| `writeBoolean`/`readBoolean`  | 1            | —                               |
| `writeMoney`/`readMoneyCents` | 8 (centavos) | `NULL_LONG`                     |
| `writeTimestamp`/`readTimestampMillis` | 8 (ms desde 1970, sem fuso) | `NULL_LONG` |

## 3. Terminal e cores

```java
Logger.info(TerminalColor.GREEN_BOLD.colorize("ok"));
String c = TerminalColor.ofHex("#ff8800");
```

## 4. Regras rápidas para agentes

1. Use `Platform.*`; não instancie `LNX*`/`WIN*` direto.
2. Código que depende de SO trata `MAC`/`UNKNOWN` (serviços lançam
   `UnsupportedOperationException`).
3. `NativeCache` sempre fechado (`try-with-resources` ou dono explícito).
4. Layout binário documentado junto do `sizer`/writer (ver `br.commons.cache`).
5. Bindings Windows: adicione função em `Kernel32`/`User32`/... seguindo o
   padrão `MethodHandle` + `FunctionDescriptor`; nunca JNI.
6. `jvmFS()` depende do diretório de trabalho — não assuma raiz do projeto.

## 5. Checklist de revisão

- [ ] Ramo para SO não suportado tratado.
- [ ] `NativeCache` fechado no fim do ciclo de vida.
- [ ] `capacity` de strings comporta o maior valor esperado (UTF-8 + 4).

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 6. Seleção de implementação

- `Platform.CURRENT` via `os.name` (`win` → WINDOWS; `nix/nux/aix` → LINUX;
  `mac` → MAC). `fileSystem()`/`terminal()`/`network()` usam holder idiom
  (uma instância, criada pela inicialização de classe da JVM); em
  `MAC`/`UNKNOWN` lançam `UnsupportedOperationException` com o `os.name`.
- `Terminal` tem fábrica própria que lança `RuntimeException("unreachable")`
  fora de Linux/Windows.
- `LNXFileSystem.jvmFS()` = `Path.of("").toAbsolutePath()`;
  `WINFileSystem.userFS()` usa `Shell32` (FFM).

## 7. Rede

- Linux: lê `/proc/net/tcp` e `/proc/net/tcp6`, considera `st == 0A`
  (LISTEN); ignora TIME_WAIT.
- Windows: `GetExtendedTcpTable` com `TCP_TABLE_OWNER_PID_LISTENER`; porta
  em network byte order convertida manualmente.
- Checar porta livre e depois abrir é corrida (TOCTOU): para servidor, tente
  `bind` e trate a falha; use `isPortInUse` só para diagnóstico.

## 8. `NativeCache` — memória e concorrência

- `Arena.ofShared()`: segmentos acessíveis de qualquer thread; índice
  `ConcurrentHashMap`, ordem `ConcurrentLinkedQueue`.
- `remove` e `put` sobre chave existente desindexam e contam bytes mortos
  (`deadBytes`); `compact(grace)` copia os vivos para nova arena e fecha a
  antiga após `grace` (ou no `close`). Escritor único: não chame `compact`
  junto com `put`/`remove`. Leitor com segmento antigo após a graça →
  `IllegalStateException` (reler a chave).
- `put` só aloca: `Arena.allocate` já devolve o segmento zerado.
- `close()` com leitor concorrente: fechar arena compartilhada em uso pode
  falhar/abortar acesso (`IllegalStateException`) no leitor. `Cache`
  captura e relê uma vez via registro (ver `cache/AGENTS.md`).
- Re-`put` de chave mantém a posição original na ordem.
- `remove` em `ConcurrentLinkedQueue` é O(n).

## 9. Codecs — armadilhas

- `writeString` trunca para `capacity - 4` bytes com WARN. `truncateUtf8`
  corta sempre em fronteira de caractere (o caractere que não cabe inteiro
  sai todo) → UTF-8 sempre válido.
- `writeUuid(null)` e UUID nil (`0-0`) são indistinguíveis.
- `writeMoney`: 2 casas `HALF_UP`; overflow de `long` vira `NULL_LONG` com
  WARN (dado perdido silenciosamente para o leitor).
- `writeTimestamp`: `LocalDateTime` tratado como UTC (sem fuso); leitor deve
  usar a mesma convenção.

## 10. Terminal

- `AbstractTerminal.render(buffer, dim, indices, count)`: escreve só células
  alteradas (`indices`), posicionando cursor com CSI `ESC[y;xH` e code points.
- Linux: `ioctl(1, TIOCGWINSZ=0x5413)` via FFM (raw mode/termios não implementado); Windows: `kernel32`
  (`GetConsoleScreenBufferInfo`, modo de console). Sem TTY → `(0,0)`.

## 11. Extensão

| Necessidade               | Onde mexer                                          |
|---------------------------|-----------------------------------------------------|
| Suporte macOS             | `provider/mac/` + `case MAC` em `Platform`          |
| Liberar memória no remove | arena por registro ou compactação periódica         |
| Nova função Win32         | binding na classe da DLL (`Kernel32`, ...)          |
| Novo codec                | método estático em `NativeCache` + teste de ida e volta |
