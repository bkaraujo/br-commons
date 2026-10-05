# br.commons.persistence — JDBC, transações, migrações e storage

Persistência sem framework: pool próprio, transação por thread com
propagação REQUIRED, repositório genérico por tabela, migrações versionadas e
um `Storage` chave-valor em arquivo JSON.

| Subpacote / tipo               | Papel                                              |
|--------------------------------|----------------------------------------------------|
| `Storage`                      | `read/write(file, jsonKey)`, `exists`, `listFiles` |
| `inmemory/InMemoryStorage`     | `Storage` em memória (testes)                      |
| `json/LocalFileStorage`        | `Storage` em arquivos JSON (Jackson)               |
| `json/Repository`, `EntityDiff`| Contrato de repositório JSON; diff para log        |
| `jdbc/DataSource`              | Pool + transações; implementa `Transactions`       |
| `jdbc/Transactions`            | `inTransaction(work)`, `afterCommit(action)`       |
| `jdbc/JDBCTransaction`         | `query`, `execute`, `commit`, `rollback`, `close`  |
| `jdbc/JDBCRepository<T>`       | CRUD genérico por tabela (introspecção de metadados) |
| `jdbc/TransactionInitializer`  | Hook no início da transação externa (ex.: RLS)     |
| `jdbc/JDBCProperties`          | url, driver, credenciais, pool, timeouts           |
| `jdbc/pool/`                   | `ConnectionPool` e wrappers                        |
| `jdbc/primitives/`             | Wrappers JDBC que devolvem `Result`                |
| `jdbc/migration/Migrator`      | `V<n>__<desc>.sql`, forward-only, com checksum e lock |

## 1. Configurar

```java
var p = new JDBCProperties();
p.name("app"); p.driver("org.postgresql.Driver"); p.url(url);
p.username(u); p.password(pw); p.minPoolSize(2); p.maxPoolSize(10);
p.connectionTimeout(5_000); p.autoCommit(false);

new Migrator(() -> DriverManager.getConnection(url, owner, ownerPw), "db/migration")
        .migrate().getOrThrow();                         // antes do pool da aplicação
var ds = new DataSource(p, tx -> tx.execute("select set_config('app.tenant', ?, true)",
        JDBCParameter.of(tenant())).map(x -> null));      // opcional (RLS)
Context.set(DataSource.class, () -> ds);                 // JDBCRepository busca aqui
```

## 2. Caso de uso transacional

```java
Result<Order, BusinessError> r = transactions.inTransaction(() ->
        repo.reserve(id).flatMap(o -> payments.charge(o)));
transactions.afterCommit(() -> MessageBus.submit(new OrderPaid(id)));
```

- Aninhado na mesma thread **participa** da transação aberta (REQUIRED).
- `Failure` ou exceção → rollback-only; só o nível externo comita.
- `afterCommit` roda após o commit externo (descartado em rollback); sem
  transação aberta, roda na hora.

## 3. Repositório por tabela

```java
final class OrderRepo extends JDBCRepository<Order> {
    OrderRepo() { super("public", "orders"); }
    protected Order map(JDBCResultSet rs) { return new Order(UUID.fromString(rs.getString("ID").get()), ...); }
    protected Map<String, Object> values(Order o) { return Map.of("ID", o.id().toString(), ...); }
    protected Set<String> updateImmutableColumns() { return Set.of("TMS_CREATE_AT"); }
}
repo.findById(id); repo.findAll(); repo.exists(id); repo.count();
repo.insert(o); repo.update(o); repo.save(o); repo.deleteById(id);
```

Valores já convertidos: `UUID#toString`, `enum#name`, `"Y"/"N"`, `Timestamp`.

## 4. SQL direto

```java
ds.query("select * from t where id = ?", JDBCParameter.of(id), rs -> ...);
ds.execute("update t set x = ? where id = ?", JDBCParameter.of(x, id));
tx.query(sql, params, fn);  tx.execute(sql, params);   // Result<..., String>
```

## 5. Regras rápidas para agentes

1. Sempre parâmetros (`?` + `JDBCParameter`); nunca concatene valores.
2. Transação de caso de uso via `Transactions.inTransaction`.
3. Efeito externo (e-mail, evento, cache) em `afterCommit`.
4. Migração nova = arquivo novo `V<n+1>__desc.sql`; **nunca** edite aplicada.
5. `DataSource` registrado no `Context` **antes** de instanciar repositórios.
6. `TransactionInitializer` só usa `tx.query`/`tx.execute(sql, ...)`; nunca
   `tx.execute(Function)` (fecha a transação).
7. Em teste sem banco: `InMemoryStorage` para `Storage`.

## 6. Checklist de revisão

- [ ] Nenhum SQL com valor concatenado.
- [ ] Migrações aplicadas intactas; nova versão sequencial.
- [ ] `values`/`map` cobrem as mesmas colunas da tabela.
- [ ] Efeitos colaterais externos em `afterCommit`.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Transações (`DataSource.begin`)

- `Map<nome, ThreadLocal<JDBCTransaction>>`: uma transação por **nome e
  thread**. Reentrância na mesma thread → `enter()` (conta nível) e devolve a
  mesma; só o `close()` do nível externo devolve a conexão.
- Abertura: adquire conexão (timeout = `connectionTimeout`), registra em
  `live`, roda `TransactionInitializer`; falha do initializer → rollback +
  close + `Failure`.
- `markRollbackOnly` envenena: o externo faz rollback mesmo com sucesso.
- `commit`/`rollback` em nível aninhado são no-op.
- `afterCommit` (de `DataSource`) olha **só** a transação de nome `"default"`;
  transação com outro nome → ação roda imediatamente, antes do commit.
- Ações pós-commit que falham são logadas e não afetam as demais.
- Thread que abre e não fecha → transação órfã (log de aviso identifica a
  thread dona). Virtual threads não herdam a transação.

## 8. Atalhos de `DataSource` e falhas

- `query`/`execute`/`transaction` abrem (ou participam de) transação e
  **lançam** em falha: `Result.get()` loga FATAL e lança `RuntimeException`.
- `mutating`: `RuntimeException` do trabalho → rollback, `Logger.fatal` e
  relança `RuntimeException("Unreachable")` **sem causa** — o tipo original
  (ex.: `BusinessException`) é perdido. Para preservar, use `inTransaction`,
  que relança a exceção original.
- `inTransaction`: falha ao abrir → `IllegalStateException`; falha no commit
  → `IllegalStateException`.
- `readOnly`: faz rollback ao fim (só no externo); nunca marca rollback-only.

## 9. `JDBCRepository`

- Construtor faz introspecção (`DatabaseMetaData.getColumns/getPrimaryKeys`)
  — exige `DataSource` no `Context` e banco acessível na construção.
- SQL gerado usa `table` **sem schema** (schema só filtra a introspecção):
  tabela fora do `search_path` falha em runtime.
- Identificadores não são citados: nome com maiúsculas/reservado depende do
  banco.
- `save` = `SELECT 1` + `UPDATE`/`INSERT` na mesma transação; **não** é
  atômico sob concorrência (duas threads podem inserir → violação de PK). Use
  `INSERT ... ON CONFLICT` quando houver concorrência.
- `findAll` sem paginação.

## 10. Pool (`ConnectionPool`)

- Contadores atômicos; `aquire(timeout)` espera até o limite → `poolExhausted`.
- Manutenção: remove ociosas além de `idleTimeout` mantendo `minPoolSize`;
  recria até o mínimo. `maxLifetime` recicla conexões antigas.
- `Class.forName(driver)` a cada conexão aberta; `autoCommit` aplicado na
  abertura. `validationQuery` opcional.
- `ConnectionWrapper.close()` devolve ao pool em vez de fechar.

## 11. Migrações

- Tabela `schema_migration(version, description, checksum, applied_at)`.
- Cada arquivo na sua transação; checksum SHA-256 verificado em todo start —
  arquivo aplicado alterado ou removido impede o start.
- Lock: PostgreSQL `pg_advisory_lock` (cobre várias máquinas); H2 lock por
  URL na JVM. Outro banco → `Failure("Unsupported database")`.
- H2: DDL comita implícito — migração que falha deixa o DDL anterior à falha.
- `pending()` para readiness check. `ConnectionFactory` usa credencial de dono
  do schema, fora do pool da aplicação.
- O `package-info` de `jdbc` mostra uma API `JDBC.register/getConnection` que
  **não existe** — ignore aquele exemplo.

## 12. Storage em JSON

- `LocalFileStorage(path, mapper)`: raiz em `Platform.fileSystem().jvmFS()`;
  cada arquivo é um objeto JSON e cada `jsonKey` um campo.
- `write` = ler arquivo inteiro → alterar campo → regravar: **não** atômico
  nem thread-safe (escritas concorrentes perdem dados; crash no meio corrompe).
- `resolve(file)` não normaliza: `file` com `..` escapa da raiz — nunca use
  nome vindo do usuário.
- `EntityDiff.of(mapper, antes, depois)`: diff textual para auditoria/log.

## 13. Extensão

| Necessidade                | Onde mexer                                          |
|----------------------------|-----------------------------------------------------|
| Upsert atômico             | `JDBCRepository.save` com SQL do dialeto            |
| Schema no SQL              | prefixar `schema.` em `findAll/insert/...`          |
| Novo banco em migração     | `MigrationLock.of` + teste de DDL transacional      |
| Preservar exceção original | `DataSource.mutating`: relançar `ex`                |
