# br.commons.cdi — registro de singletons

`Context` é um registro global `Class → instância`, thread-safe, usado como
injeção de dependência mínima (service locator).

## 1. Uso

```java
Context.set(Repo.class, () -> new JdbcRepo(ds));   // registra instância criada pelo supplier
Context.set(Clock.class);                           // instancia via construtor público sem args
Repo repo = Context.get(Repo.class);                // lança IllegalStateException (msg localizada) se ausente
Cache c = Context.tryGet(Cache.class);              // classe concreta: cria (construtor vazio) e registra se ausente
Repo r3 = Context.tryGet(Repo.class, JdbcRepo::new);
Context.remove(Repo.class);
Context.clear();                                    // testes
```

## 2. Regras rápidas para agentes

1. Registre no bootstrap; consuma com `get` (falha cedo se faltar).
2. A chave é a classe pedida, não a da instância: `set(Repo.class, ...)` não
   responde a `get(JdbcRepo.class)`.
3. `set(type)`/`tryGet(type)` sem supplier exigem classe concreta com
   construtor público sem argumentos.
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

- `get` lê o `ConcurrentHashMap` direto, sem lock.
- `set` executa o supplier **fora** do lock e só publica dentro dele: supplier
  lento não bloqueia leituras.
- `tryGet` (check-then-put), `remove` e `clear` rodam via
  `Threads.locked(ReentrantLock, ...)`; `tryGet` chama o supplier dentro do
  lock (não pode criar duas vezes) — supplier que espera outra thread que usa
  `Context` → deadlock.

## 5. Armadilhas

- `set(type)` e `tryGet(type)` usam `Meta.instance`, que chama
  `exit(99, ...)` se a instanciação falhar — encerra a JVM em vez de lançar.
- `set` sobrescreve registro existente silenciosamente (só log DEBUG;
  `tryGet` loga o registro em VERBOSE).
- Mensagem de `get` sem registro vem de `Errors.get().noInstanceRegistered`.
- Exceção checked dentro do lock vira `IllegalStateException`
  (comportamento de `Threads.locked`).

## 6. Extensão

| Necessidade              | Onde mexer                                        |
|--------------------------|---------------------------------------------------|
| Escopo por request       | não use `Context`; passe por parâmetro            |
| Lazy singleton           | registrar `Supplier` memoizado, ou `tryGet`       |
| Fechar recursos no fim   | `clear()` não chama `close()`; adicionar hook     |
