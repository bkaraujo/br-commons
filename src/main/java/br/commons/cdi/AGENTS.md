# br.commons.cdi — registro de singletons

`Context` é um registro global `Class → instância`, thread-safe, usado como
injeção de dependência mínima (service locator).

## 1. Uso

```java
Context.set(Repo.class, () -> new JdbcRepo(ds));   // registra instância criada pelo supplier
Context.set(Clock.class);                           // instancia via construtor público sem args
Repo repo = Context.get(Repo.class);                // lança IllegalStateException se ausente
Repo r2 = Context.tryGet(Repo.class);               // cria (construtor vazio) e registra se ausente
Repo r3 = Context.tryGet(Repo.class, JdbcRepo::new);
Context.remove(Repo.class);
Context.clear();                                    // testes
```

## 2. Regras rápidas para agentes

1. Registre no bootstrap; consuma com `get` (falha cedo se faltar).
2. A chave é a classe pedida, não a da instância: `set(Repo.class, ...)` não
   responde a `get(JdbcRepo.class)`.
3. `tryGet` sem supplier exige construtor público sem argumentos.
4. Em testes, `Context.clear()` no `@BeforeEach`/`@AfterEach`.
5. Não guarde objetos com ciclo de vida curto (request/sessão).

## 3. Checklist de revisão

- [ ] Toda dependência usada via `get` é registrada no bootstrap.
- [ ] Testes limpam o contexto.
- [ ] Interface usada como chave, implementação no supplier.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 4. Concorrência

- Todas as operações rodam via `Threads.locked(ReentrantLock, ...)`; o mapa é
  `ConcurrentHashMap`, mas o lock é que garante atomicidade de `tryGet`
  (check-then-put).
- `set` chama `supplier.get()` **dentro** do lock. Supplier lento bloqueia
  todo `get`. Supplier que chama `Context` funciona (lock reentrante), mas
  supplier que espera outra thread que usa `Context` → deadlock.
- `get` também toma o lock: leitura serializada. Se virar gargalo, `get` pode
  ler o `ConcurrentHashMap` direto, sem lock.

## 5. Armadilhas

- `Context.create` é **idêntico** a `get` (não cria nada, lança se ausente).
  Não use esperando criação; prefira `tryGet`. Candidato a remoção ou a
  implementar "sempre nova instância".
- `set(type)` usa `Meta.instance`, que chama `exit(99, ...)` se a
  instanciação falhar — encerra a JVM em vez de lançar.
- `set` sobrescreve registro existente silenciosamente (só log DEBUG).
- Exceção checked dentro do lock vira `IllegalStateException`
  (comportamento de `Threads.locked`).

## 6. Extensão

| Necessidade              | Onde mexer                                        |
|--------------------------|---------------------------------------------------|
| Escopo por request       | não use `Context`; passe por parâmetro            |
| Lazy singleton           | registrar `Supplier` memoizado, ou `tryGet`       |
| Fechar recursos no fim   | `clear()` não chama `close()`; adicionar hook     |
