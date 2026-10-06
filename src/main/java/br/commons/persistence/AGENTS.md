# br.commons.persistence — JDBC, transações, migrações e storage

Persistência sem framework: pool próprio, transação por thread com
propagação REQUIRED, repositório genérico por tabela, migrações versionadas e
um `Storage` chave-valor em arquivo JSON.

| Subpacote / tipo               | Papel                                              |
|--------------------------------|----------------------------------------------------|
| `Storage`                      | `read/write(file, jsonKey)`, `exists`, `listFiles` |
| `inmemory/InMemoryStorage`     | `Storage` em memória (testes); `clear()`           |
| `json/LocalFileStorage`        | `Storage` em arquivos JSON (Jackson)               |
| `json/Repository`, `EntityDiff`| Contrato (sem implementação na lib); diff para log |
| `jdbc/DataSource`              | Pool + transações; implementa `Transactions`       |
| `jdbc/Transactions`            | `inTransaction(work)`, `afterCommit(action)`       |
| `jdbc/JDBCTransaction`         | `query`, `execute`, `commit`, `rollback`, `close`  |
| `jdbc/JDBCRepository<T>`       | CRUD genérico por tabela (introspecção de metadados) |
| `jdbc/TransactionInitializer`  | Hook no início da transação externa (ex.: RLS); `NONE` |
| `jdbc/JDBCProperties`          | url, driver, credenciais, pool, timeouts           |
| `jdbc/Results`                 | Falhas `Result<T, String>` com texto i18n (`Errors`) |
| `jdbc/pool/`                   | `ConnectionPool` e wrappers                        |
| `jdbc/primitives/`             | Wrappers JDBC que devolvem `Result`                |
| `jdbc/migration/Migrator`      | `V<n>__<desc>.sql`, forward-only, com checksum e lock |
| `jdbc/migration/Migration`     | record `(version, description, sql, checksum)`     |

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
PK composta: `findById/exists/deleteById(Object... pk)` na ordem de `primaryKeys()`.

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
- `begin()`/`begin(name)`/`begin(timeout)`/`begin(name, timeout)` são
  públicos; nome padrão `"default"`, timeout padrão `connectionTimeout`.
- Abertura: adquire conexão, registra em `live`, roda `TransactionInitializer`;
  falha do initializer → rollback + close + `Failure`
  (`Errors.transactionInitializerFailed`).
- `markRollbackOnly` envenena: o externo faz rollback mesmo com sucesso.
- `commit`/`rollback` em nível aninhado são no-op.
- `afterCommit` (de `DataSource`) anexa à transação externa mais recente
  aberta na thread, qualquer que seja o nome; sem transação → roda na hora.
- Ações pós-commit que falham são logadas e não afetam as demais.
- Thread que abre e não fecha → transação órfã: `DataSource.close()` faz
  rollback e loga WARN com nome, thread dona e profundidade. Virtual threads
  não herdam a transação.

## 8. Atalhos de `DataSource` e falhas

- `query`/`execute`/`transaction` abrem (ou participam de) transação e
  **lançam** em falha: `Failure` (abrir, SQL) → `IllegalStateException` com a
  mensagem; commit → `IllegalStateException` (`Errors.commitFailed`);
  `RuntimeException` do trabalho → rollback e relançada **intacta** (tipo e
  causa, ex.: `BusinessException`).
- `inTransaction`: falha ao abrir ou no commit → `IllegalStateException` com
  a mensagem; `Failure` do trabalho → rollback e devolvida.
- `query` (readOnly): faz rollback ao fim (só no externo); nunca marca
  rollback-only. `execute`/`transaction` (mutating) comitam.
- Monitoração: `getActiveConnections/getAvailableConnections/getTotalConnections`,
  `properties()` (cópia), `isClosed/isOpened`, `close()`.

## 9. `JDBCRepository`

- Construtor faz introspecção (`DatabaseMetaData.getColumns/getPrimaryKeys`)
  — exige `DataSource` no `Context` e banco acessível na construção.
- Todo SQL gerado usa `qualifiedTable()` (`schema.table` quando há schema).
- Identificadores não são citados: nome com maiúsculas/reservado depende do
  banco.
- `save` = upsert nativo: PostgreSQL `INSERT ... ON CONFLICT (pk) DO UPDATE`
  (atômico); H2 `MERGE ... USING`, repetido até 3× em violação de PK (23505),
  porque o MERGE do H2 não é atômico entre transações; esgotado →
  `IllegalStateException` (`Errors.saveFailed`). Respeita
  `updateImmutableColumns`. Banco desconhecido → SELECT + INSERT/UPDATE na
  mesma transação (ainda sujeito a corrida).
- `findAll` sem paginação. `insert`/`update`/`save` devolvem a entidade.

## 10. Pool (`ConnectionPool`)

- Contadores atômicos; `aquire(timeout)` espera até o limite →
  `Results.poolExhausted` (`Errors.poolTimeout`). Pool fechado →
  `resourceIsClosed`; conexão inválida é descartada e a aquisição repetida.
- Manutenção: remove ociosas além de `idleTimeout` mantendo `minPoolSize`;
  recria até o mínimo. `maxLifetime` recicla conexões antigas.
- `Class.forName(driver)` a cada conexão aberta (`jdbcDriverNotFound`);
  `autoCommit` aplicado na abertura. `validationQuery` opcional. Falha ao
  criar o mínimo no construtor → `RuntimeException` (`poolInitFailed`).
- `ConnectionWrapper.close()` devolve ao pool em vez de fechar.

## 11. Migrações

- Tabela `schema_migration(version, description, checksum, applied_at)`.
- Cada arquivo na sua transação; checksum SHA-256 verificado em todo start —
  arquivo aplicado alterado ou removido impede o start.
- Lock: PostgreSQL `pg_advisory_lock` (cobre várias máquinas); H2 lock por
  URL na JVM. Outro banco → `Failure` (`unsupportedDatabase`).
- `location` = diretório do classpath (FS e jars; todas as entradas);
  versão duplicada → `Failure`. Mensagens de falha vêm de `Errors`
  (`migrationFailed`, `appliedMigrationMissing/Changed`…).
- H2: DDL comita implícito — migração que falha deixa o DDL anterior à falha.
- `pending()` para readiness check. `ConnectionFactory` usa credencial de dono
  do schema, fora do pool da aplicação.

## 12. Storage em JSON

- `LocalFileStorage(path, mapper)`: raiz em `Platform.fileSystem().jvmFS()`;
  cada arquivo é um objeto JSON e cada `jsonKey` um campo.
- `write` = ler → alterar campo → regravar, serializado por lock por arquivo
  (compartilhado entre instâncias na JVM) e publicado por `*.tmp` +
  `ATOMIC_MOVE`. Outro processo escrevendo o mesmo arquivo ainda corre.
- `read`/`write` devolvem `Result`; nome que sai da raiz (`..`, caminho
  absoluto) → `Failure` sem tocar o disco; `exists` → `false`.
- `listFiles(prefix, suffix)`: só a raiz (sem recursão); erro de IO →
  `UncheckedIOException`.
- `EntityDiff.of(mapper, antes, depois)`: diff textual para auditoria/log.

## 13. Extensão

| Necessidade                | Onde mexer                                          |
|----------------------------|-----------------------------------------------------|
| Upsert de novo banco       | `JDBCRepository.upsertSql` (novo `case` do produto) |
| Novo banco em migração     | `MigrationLock.of` + teste de DDL transacional      |
| Preservar exceção original | `DataSource.mutating`: relançar `ex`                |
