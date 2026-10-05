# br.commons.cache — cache off-heap particionado

Cache genérico de modelos de domínio, dividido em **partições** independentes
(chave `String` opaca escolhida pelo chamador: tenant, conta, região...), guardado fora do heap
(`br.commons.platform.NativeCache`), com manutenção serializada numa única
thread e API que nunca lança.

| Tipo               | Papel                                                         |
|--------------------|---------------------------------------------------------------|
| `AbstractCache<T>` | Base: populate por partição, leitura rápida, upsert/evict     |
| `CacheWorker`      | Thread única `cache-worker`; tarefas devolvem `Result`        |

## 1. Definir um cache

Subclasse informa: prefixo (mensagens), loader, chave, tamanho do registro,
escritor e leitor binários.

```java
final class ItemCache extends AbstractCache<Item> {
    ItemCache(ItemRepo repo) {
        super("ITEM:",
              repo::listByPartition,       // Function<String, List<Item>>
              Item::id,                    // chave UUID
              _ -> 24,                     // bytes do registro
              (seg, it) -> {               // escrita
                  NativeCache.writeUuid(seg, 0, it.id());
                  seg.set(ValueLayout.JAVA_LONG_UNALIGNED, 16, it.amount());
              });
    }
    @Override protected Item mapToDomain(MemorySegment seg) {      // leitura
        return new Item(NativeCache.readUuid(seg, 0),
                        seg.get(ValueLayout.JAVA_LONG_UNALIGNED, 16));
    }
}
```

## 2. Ciclo de vida

```java
cache.load(partition);                  // popula async (loader na cache-worker)
cache.list(partition);                  // Result<List<T>, String>
cache.find(partition, id);              // Result<T, String>; miss = failure "not found: ITEM:<id>"
cache.upsert(partition, model);         // async
cache.evict(partition, id);             // async
cache.evictEverywhere(id);              // todas as partições
cache.unload(partition);                // fecha memória nativa
```

Mutações devolvem `CompletableFuture<Result<Void,String>>`: ignore se só
enfileira; `join()` em teste/diagnóstico.

## 3. Leitura

- Cache pronto → leitura direta, sem lock, sem alocação extra.
- Populate em andamento → espera até **2 s**; timeout vira `Failure`.
- Partição não carregada → `list` = lista vazia; `find` = `not found`.

## 4. `CacheWorker`

```java
CacheWorker.submit(() -> ...);          // Runnable → Result
CacheWorker.submitResult(() -> Result.success());
CacheWorker.supply(() -> valor);        // Result<T,String>
CacheWorker.waitForPending();           // testes: espera fila (FIFO) sem desligar
CacheWorker.awaitIdle();                // shutdown definitivo (fim da app)
```

## 5. Regras rápidas para agentes

1. Nunca deixe exceção sair de evento que mexe em cache: use a API `Result`.
2. Layout binário: `sizer` e `writer`/`mapToDomain` precisam concordar em
   offsets e tamanho — mude os três juntos.
3. Use `*_UNALIGNED` nos acessos (`ValueLayout.JAVA_LONG_UNALIGNED`).
4. Em testes, `CacheWorker.waitForPending()` antes de asserts pós-mutação.
5. Nunca chame `awaitIdle()` em teste: desliga o worker para sempre.
6. Sempre `unload` quando a partição não for mais usada (memória nativa não é coletada).
7. Exemplo completo (cache por sessão de usuário): `src/main/examples/br/commons/examples/cache/session`.
8. Teste de referência: `src/test/java/br/commons/cache/AbstractCacheTest.java` — `ItemCache`
   mínimo (uuid + long, 24 bytes) e partições `east`/`west`; cobre hit/miss, partição
   não carregada, ordem do `list`, upsert/evict, `evictEverywhere`, `unload` e
   isolamento entre partições. Mudou a API? Ajuste e rode:
   `mvn test -Dtest=AbstractCacheTest`.

## 6. Checklist de revisão

- [ ] Tamanho do `sizer` cobre todos os campos escritos.
- [ ] `mapToDomain` lê os mesmos offsets que o `writer` escreve.
- [ ] Toda partição carregada tem `unload` correspondente.
- [ ] Testes esperam o worker antes de verificar.
- [ ] `AbstractCacheTest` cobre o comportamento novo e passa.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Estrutura interna

`registry: ConcurrentHashMap<partition, CompletableFuture<NativeCache<UUID>>>`.

- `load` cria o future, registra e enfileira o populate. O future é
  completado no `finally` mesmo se o loader lançar → leitores não ficam
  presos; o erro vira `Failure` do populate.
- Falhas de `store` por item são acumuladas: populate parcial devolve
  `Failure("cache populate incomplete ...")`, mas o cache fica utilizável.
- `live()` = caminho quente: `getNow(null)` + `isClosed()`.
- `awaited()` = caminho raro: `get(2s)`, trata timeout/interrupt.
- Mutações (`onCache`) rodam na worker; se o cache ainda não está pronto ou
  fechou, a mutação é **descartada com sucesso** (o populate vai ler a fonte).

## 8. Armadilhas

- `load` duas vezes para a mesma partição sobrescreve o future **sem fechar**
  o cache anterior → vazamento de memória nativa. Faça `unload` antes ou
  trate recarga.
- Upsert enviado durante o populate pode ser descartado (cache ainda não
  pronto) e o loader pode ter lido estado antigo → janela de inconsistência
  logo após `load`.
- `CacheWorker.WORKER` é estático e único: `awaitIdle()` é irreversível; toda
  submissão posterior vira `Failure` (`RejectedExecutionException`).
- Leituras rodam em qualquer thread, concorrentes com mutações na worker;
  a segurança disso depende de `NativeCache` (ver AGENTS de `platform`).
- Instrumentação: `list`/`find` só medem tempo com DEBUG ligado para
  `AbstractCache` (gate com `CALLER` constante); mutações via
  `Execution.nanos(CALLER, ...)`.

## 9. Por que `Result` dentro de `CompletableFuture`

Exceção da tarefa só existe depois, na worker. `Result` síncrono seria falso
sucesso. `verdict` desembrulha `CompletionException`, loga em ERROR e devolve
`Failure(causa.toString())`. Publicador de evento (dentro de transação) nunca
é derrubado por falha de cache.

## 10. Extensão

| Necessidade                 | Onde mexer                                        |
|-----------------------------|---------------------------------------------------|
| Chave não-UUID              | generalizar `NativeCache<UUID>`/`keyer`           |
| Registro de tamanho variável| `sizer` por modelo já permite; cuide do layout    |
| Recarga segura              | `load`: fechar future anterior se existir         |
| Paralelizar caches          | um worker por cache (hoje global, ordem FIFO)     |
