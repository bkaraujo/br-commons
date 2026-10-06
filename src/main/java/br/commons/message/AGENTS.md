# br.commons.message — mensagens in-process

Barramento de mensagens síncrono e in-process do br-engine: serviço e
contratos no mesmo pacote.

| Tipo               | Papel                                                        |
|--------------------|--------------------------------------------------------------|
| `MessageBus`       | Serviço estático: `subscribe`, `unsubscribe`, `submit`, `reset` |
| `Message`          | Interface marcadora de toda mensagem publicável              |
| `MessageListener`  | Anotação (`METHOD`, `RUNTIME`) que marca um consumidor       |
| `MessageResult`    | Retorno obrigatório do consumidor: `CONSUMED` ou `AVAILABLE` |
| `MessageProcessor` | Par `(container, MethodHandle)` criado pelo bus; uso interno |

## 1. Definir uma mensagem

Prefira `record` imutável que implementa `Message` (ou uma subinterface, como
`br.commons.business.BusinessEvent`):

```java
public record OrderPlaced(String orderId, BigDecimal total) implements Message {}
```

## 2. Escrever um consumidor

Método **público**, anotado com `@MessageListener`, com **exatamente um
parâmetro** atribuível a `Message` e retorno **`MessageResult`**:

```java
public final class OrderNotifier {

    @MessageListener
    public MessageResult onOrderPlaced(OrderPlaced event) {
        Mail.send(event.orderId());
        return MessageResult.CONSUMED;
    }
}
```

Assinatura fora dessas regras não lança exceção: o método é ignorado e o bus
registra um `Logger.warn`. Verifique o retorno de `subscribe` (contagem de
métodos aceitos) quando a assinatura for nova.

## 3. Inscrever o consumidor

```java
int n = MessageBus.subscribe(new OrderNotifier()); // instância pronta
int m = MessageBus.subscribe(OrderNotifier.class);  // instancia via Meta.instance
```

- Uma inscrição por **instância** (identidade). Duas instâncias da mesma classe
  são duas inscrições; repetir a mesma instância (ou a mesma classe em
  `subscribe(Class)`) só gera warn e devolve a contagem anterior.
- `MessageBus.unsubscribe(obj)` / `unsubscribe(Classe.class)` remove e devolve
  quantos listeners saíram. `MessageBus.reset()` limpa tudo — só para testes.

## 4. Publicar

```java
MessageBus.submit(new OrderPlaced("A-17", new BigDecimal("99.90")));
```

- Execução **síncrona**, na thread de quem publica.
- **Todos** os consumidores rodam (fan-out completo).
- Mensagem sem consumidor: no-op silencioso.
- Exceção de consumidor **propaga** para o `submit`; consumidores seguintes
  não rodam. `RuntimeException`/`Error` sobem intactas; checked vêm em
  `RuntimeException` com texto i18n `Errors.get().messageProcessingFailed`.

## 5. `MessageResult`

`CONSUMED` e `AVAILABLE` são **informativos**: o bus ignora o valor e não
interrompe o fan-out. Use `CONSUMED` quando o consumidor agiu sobre a mensagem
e `AVAILABLE` quando a ignorou (filtro não casou etc.). Não implemente lógica
de "primeiro consumidor vence" com base nisso.

## 6. Regras rápidas para agentes

1. Mensagem = `record implements Message`; nunca mutável.
2. Consumidor = método `public`, 1 parâmetro, retorna `MessageResult`.
3. Parâmetro pode ser tipo concreto, superclasse ou interface (`Message`,
   `BusinessEvent`, família própria) — veja §8.1.
4. Cada instância inscrita recebe mensagens; não inscreva a mesma lógica 2x.
5. Não capture exceções no `submit` só para "seguir em frente": a propagação
   existe para o publicador desfazer a própria transação.
6. `Message` não carrega estado: horário do evento é componente do record.
7. Em testes, chame `MessageBus.reset()` em `@BeforeEach` ou use tipos de
   mensagem únicos por teste (padrão de `MessageBusTest`).

## 7. Checklist de revisão

- [ ] Tipo da mensagem implementa `Message` e é imutável.
- [ ] Todo `@MessageListener` retorna `MessageResult` e tem 1 parâmetro.
- [ ] Listener de interface ampla (`Message`) é intencional (recebe tudo).
- [ ] `subscribe` chamado uma vez por instância; retorno conferido.
- [ ] Consumidor não depende de ordem relativa a outros da mesma classe.
- [ ] Teste cobre caminho de exceção quando o publicador tem transação.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 8. Semântica de despacho e inscrição

### 8.1 Despacho percorre superclasses e interfaces

`MessageBus.buildDispatchList` faz busca em largura a partir da classe
concreta: em cada nível adiciona a superclasse e as interfaces diretas, até
`Object` (exclusive). `LinkedHashSet` garante que cada tipo contribua uma vez,
mesmo em diamante (`A extends Message`, `B extends Message`, `X implements A, B`).

```java
interface AuditEvent extends Message {}
record LoginFailed(String user) implements AuditEvent {}

@MessageListener
public MessageResult audit(AuditEvent e) { ... }      // recebe LoginFailed

@MessageListener
public MessageResult all(Message m) { ... }           // recebe TODA mensagem
```

Listener em `Message` é um "tap" global (log, métricas): roda em cada `submit`.

### 8.2 `Message` é marcador puro

Antes havia `long timestamp = Time.millis()` na interface — campo de interface
é `static final`, então era um valor único da carga da classe. Foi removido.
Se precisar de horário, declare componente: `record X(long at) implements Message`.

### 8.3 Deduplicação por identidade

- `subscribe(Object)`: `IdentityHashMap<Object, Integer>`. Mesma instância →
  warn + contagem anterior; outra instância da mesma classe → nova inscrição.
- `subscribe(Class)`: `HashMap<Class<?>, Integer>`. Mesma classe → warn,
  sem criar nova instância via `Meta.instance`.
- Instâncias inscritas ficam referenciadas até `unsubscribe`/`reset()`;
  objeto de vida curta inscrito precisa de `unsubscribe` ao fim (ex.: `close()`).

### 8.4 Somente métodos públicos

`subscribe` usa `getClass().getMethods()`: métodos públicos, incluindo
herdados. Métodos `protected`/package/`private` anotados são ignorados **sem
warn**. `setAccessible(true)` existe para permitir classes não públicas
(anônimas, locais, aninhadas) com métodos públicos.

### 8.5 `unsubscribe`

- `unsubscribe(Object)`: casa por identidade; remove todos os listeners da
  instância e devolve quantos eram. Não inscrito → warn + `0`.
- `unsubscribe(Class)`: remove a instância que `subscribe(Class)` criou.
- Mesmo mecanismo do `subscribe`: sob `LOCK`, copia `processors` sem a
  instância e publica `Registry` novo (via `publish`). `submit` já em curso
  termina no snapshot antigo — o listener removido ainda pode rodar uma vez.
- Depois do `unsubscribe` a instância pode ser inscrita de novo.

## 9. Ordem de execução

Lista de despacho = concatenação, do tipo mais geral para o concreto (inverso
da busca em largura do §8.1: interfaces de topo como `Message` primeiro), das
listas registradas por tipo de parâmetro. Dentro de um mesmo tipo, ordem
de inscrição; dentro de uma mesma classe, ordem de `getMethods()` — **não
especificada pela JVM**. Não dependa dela. Se ordem importar, encadeie
mensagens (o consumidor A publica `XDone`, ouvido por B).

## 10. Concorrência e cache

Estado do bus = `volatile Registry(processors, dispatchCache)`:

- `processors`: `Map.copyOf` com listas `List.copyOf` — imutável.
- `dispatchCache`: `ConcurrentHashMap` por classe concreta, derivado
  exclusivamente do `processors` do **mesmo** snapshot.
- `subscribe`/`unsubscribe`/`reset` rodam via `Threads.locked(LOCK, ...)` (`ReentrantLock`, pois as sobrecargas `Class` chamam as `Object` já com o lock): copiam o mapa, registram, congelam e
  trocam o `Registry` inteiro (cache novo e vazio). Inscrição sem métodos
  aceitos não troca o snapshot.
- `submit` lê `registry` uma vez e usa só aquele snapshot; não toma lock. Um
  `submit` em curso durante um `subscribe` vê o estado anterior inteiro ou o
  novo inteiro — nunca parcial, e nunca grava lista obsoleta no cache novo.
- Custo: `subscribe` é O(total de processors) por cópia — barato no bootstrap,
  evite em caminho quente.
- Consumidores rodam na thread do publicador; estado compartilhado do
  consumidor é responsabilidade dele.

## 11. Transações e exceções

O contrato documentado em `MessageBus.submit`: exceção de consumidor sobe ao
publicador, que tipicamente faz rollback — `DataSource` usa propagação
REQUIRED por thread, então consumidores participam da transação aberta pelo
publicador. Consequências:

- Consumidor que grava no banco grava na mesma transação do publicador.
- Falha em consumidor "best-effort" derruba a operação principal. Se o efeito
  for opcional, capture **dentro** do consumidor, logue e retorne `AVAILABLE`.
- Efeitos externos não transacionais (e-mail, HTTP) disparados por consumidor
  acontecem mesmo que um consumidor posterior falhe e cause rollback. Para
  isso, publique após commit ou enfileire.

## 12. `MessageProcessor` por dentro

```java
record MessageProcessor(Object container, MethodHandle handle)
```

- `handle` vem de `MethodHandles.lookup().unreflect(method)` — handle virtual
  com receptor explícito, invocado via `handle.invoke(container, message)`.
- `invoke` (não `invokeExact`) adapta tipos: o parâmetro estático é
  `Message`, o handle espera o subtipo; cast acontece no call site.
- Retorno é cast para `MessageResult`. Hoje o bus descarta o valor.
- `Throwable` checked → `RuntimeException` cuja mensagem vem de
  `Errors.get().messageProcessingFailed(Meta.fqn(message), cause)` (bundle
  `br/commons/locale/l10n/errors*.properties`, chave `messageProcessingFailed`);
  `RuntimeException`/`Error` relançadas sem embrulho.

## 13. Pontos de extensão sugeridos

| Necessidade                 | Onde mexer                                     |
|-----------------------------|------------------------------------------------|
| Despacho assíncrono         | novo `submitAsync` com `Executor`; não altere `submit` (semântica transacional) |
| Curto-circuito por `CONSUMED` | flag opcional; hoje é proibido por design (ver javadoc de `submit`) |
| Ordem explícita             | atributo `int order()` em `@MessageListener` + sort em `buildDispatchList` |

Qualquer mudança de semântica deve vir com teste em
`src/test/java/br/commons/message/MessageBusTest.java`, mantendo o padrão de um tipo de
mensagem único por teste para não sofrer com o estado estático do bus.
