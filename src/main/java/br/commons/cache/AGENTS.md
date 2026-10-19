# br.commons.cache — cache off-heap particionado

Servidor de cache off-heap (`br.commons.platform.NativeCache`), módulo da aplicação
como o `WebServer`. Cada **região** guarda um tipo de modelo, dividida em **partições**
(chave `String` opaca: tenant, conta, usuário...). Escrita serializada numa única thread;
leitura direta na memória. API nunca lança.

| Tipo             | Papel                                                              |
|------------------|--------------------------------------------------------------------|
| `CacheServer`    | `Lifecycle`; registra regiões; dono da thread `cache-worker`       |
| `CacheRegion<T>` | Definição da região: `name`, `load`, `key`, `size`/`write`/`read`  |
| `Cache<T>`       | Proxy devolvido pelo `register`: lê direto, escreve via worker      |
| `CacheWorker`    | Interno (package-private): a fila única de escrita                 |

## 1. Registrar uma região

```java
record ItemRegion(ItemRepo repo) implements CacheRegion<Item> {
    public String name() { return "ITEM"; }                       // mensagens: "ITEM:<id>"
    public List<Item> load(String partition) { return repo.listByPartition(partition); }
    public UUID key(Item it) { return it.id(); }
    public long size(Item it) { return 24; }                      // bytes do registro
    public void write(MemorySegment seg, Item it) {
        NativeCache.writeUuid(seg, 0, it.id());
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, 16, it.amount());
    }
    public Item read(MemorySegment seg) {
        return new Item(NativeCache.readUuid(seg, 0), seg.get(ValueLayout.JAVA_LONG_UNALIGNED, 16));
    }
}

val cacheServer = new CacheServer();
Cache<Item> items = cacheServer.register(new ItemRegion(repo));
// Module.of(consumidor).attach(Module.of(cacheServer)) — cache sobe antes de quem o usa
```

## 2. Ciclo de vida

```java
cache.load(partition);                  // popula async (loader na cache-worker)
cache.list(partition);                  // Result<List<T>, String>
cache.find(partition, id);              // Result<T, String>; miss = failure `cacheNotFound` ("Não encontrado: ITEM:<id>")
cache.upsert(partition, model);         // async
cache.evict(partition, id);             // async
cache.evictEverywhere(id);              // todas as partições
cache.unload(partition);                // fecha memória nativa
cache.unloadAll();                      // todas as partições da região
```

`load`, `unload` e mutações devolvem `CompletableFuture<Result<Void,String>>`:
ignore se só enfileira; `join()` em teste/diagnóstico.

## 3. Leitura

- Cache pronto → leitura direta, sem lock, sem alocação extra.
- Populate em andamento → espera até **2 s**; timeout vira `Failure`.
- Partição não carregada → `list` = lista vazia; `find` = falha `cacheNotFound`.

## 4. `CacheServer` (ciclo de vida)

`initialize()` cria a thread de plataforma não-daemon `cache-worker` (segura o
processo, como o `WebServer`). `terminate()` drena as escritas, **libera** toda
região (descarrega as partições) e encerra a thread. Região liberada: leitura e
escrita → `Failure` `cacheRegionClosed` ("Região de cache ITEM foi liberada...");
registre de novo após um novo `initialize()`. Antes do `initialize()`, escrita
(inclusive `load`) → `Failure` `cacheWorkerNotRunning`; leitura e `register` funcionam.

```java
cacheServer.initialize();
cacheServer.waitForPending();          // testes: espera escritas enviadas (FIFO)
cacheServer.terminate();
```

## 5. Regras rápidas para agentes

1. Nunca deixe exceção sair de evento que mexe em cache: use a API `Result`.
2. Layout binário: `size`, `write` e `read` da região precisam concordar em
   offsets e tamanho — mude os três juntos.
3. Use `*_UNALIGNED` nos acessos (`ValueLayout.JAVA_LONG_UNALIGNED`).
4. Em testes, `cacheServer.waitForPending()` (ou `join()` do future) antes de asserts pós-mutação.
5. Em teste, servidor próprio: `initialize()` em `@BeforeAll`, `terminate()` em `@AfterAll`.
6. Sempre `unload` quando a partição não for mais usada (memória nativa não é coletada).
7. Exemplo completo (cache por sessão de usuário): `src/main/examples/br/commons/examples/cache/session`.
8. Teste de referência: `src/test/java/br/commons/cache/CacheTest.java` — `ItemRegion`
   mínimo (uuid + long, 24 bytes) e partições `east`/`west`; cobre hit/miss, partição
   não carregada, ordem do `list`, upsert/evict, `evictEverywhere`, `unload`, isolamento,
   corridas leitura×unload/compactação, reload e `terminate`. Mudou a API? Ajuste e rode:
   `mvn test -Dtest=CacheTest`.

## 6. Checklist de revisão

- [ ] `size` cobre todos os campos escritos.
- [ ] `read` lê os mesmos offsets que o `write` escreve.
- [ ] Toda partição carregada tem `unload` correspondente.
- [ ] Testes esperam o worker antes de verificar.
- [ ] `CacheTest` cobre o comportamento novo e passa.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Estrutura interna

`registry: ConcurrentHashMap<partition, CompletableFuture<NativeCache<UUID>>>`.

- `load` cria o future, registra e enfileira o populate. O future é
  completado no `finally` mesmo se o loader lançar → leitores não ficam
  presos; o erro vira `Failure` do populate.
- Falhas de `store` por item são acumuladas: populate parcial devolve
  `Failure` com `cachePopulateIncomplete`, mas o cache fica utilizável.
- `live()` = caminho quente: `getNow(null)` + `isClosed()`.
- `awaited()` = caminho raro: `get(2s)`, trata timeout/interrupt.
- Mutações (`onCache`) rodam na worker; se a partição não foi carregada, o cache
  ainda não está pronto ou fechou, a mutação é **descartada com sucesso** (o
  populate vai ler a fonte). Região liberada (`closed`) → `cacheRegionClosed`.
- Leitor que pega arena fechada no meio (`IllegalStateException`) relê uma vez
  do registro (`tolerantList`/`tolerantFind`); 2ª falha = ausência.

## 8. Armadilhas

- `load` de partição já carregada substitui o cache e fecha o anterior
  quando o populate dele termina; leitores que ainda o seguravam releem do
  registro.
- Após cada `upsert`/`evict` na worker, se bytes mortos > metade do alocado,
  `NativeCache.compact` copia os vivos para nova arena (antiga fecha após
  `COMPACT_GRACE` = 1 s).
- Upsert enviado durante o populate pode ser descartado (cache ainda não
  pronto) e o loader pode ter lido estado antigo → janela de inconsistência
  logo após `load`.
- Escrita sem worker rodando vira `Failure` `cacheWorkerNotRunning`; em região já
  liberada pelo `terminate()`, `cacheRegionClosed`. A thread é não-daemon: segura
  o processo até o `terminate()` (shutdown hook do `br.Application`).
- `load` antes do `initialize()` falha na hora (`cacheWorkerNotRunning`) sem
  registrar a partição: leitura dela = não carregada, sem esperar.
- Leituras rodam em qualquer thread, concorrentes com mutações na worker;
  a segurança disso depende de `NativeCache` (ver AGENTS de `platform`).
- Instrumentação: `list`/`find` só medem tempo com DEBUG ligado para
  `Cache` (gate com `CALLER` constante); mutações via
  `Execution.nanos(CALLER, ...)`.

## 9. Por que `Result` dentro de `CompletableFuture`

Exceção da tarefa só existe depois, na worker. `Result` síncrono seria falso
sucesso. `CacheWorker.verdict` desembrulha `CompletionException`, loga em ERROR e devolve
`Failure(causa.toString())`. Publicador de evento (dentro de transação) nunca
é derrubado por falha de cache.

## 10. Extensão

| Necessidade                 | Onde mexer                                        |
|-----------------------------|---------------------------------------------------|
| Chave não-UUID              | generalizar `NativeCache<UUID>`/`CacheRegion.key` |
| Registro de tamanho variável| `CacheRegion.size` por modelo já permite; cuide do layout |
| Paralelizar regiões         | um worker por região (hoje um por `CacheServer`, FIFO) |
