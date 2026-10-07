# br.commons.business — erros e eventos de domínio

Tipos que o caso de uso usa para falar de regra de negócio sem depender de HTTP,
banco ou idioma.

| Tipo                | Papel                                                          |
|---------------------|----------------------------------------------------------------|
| `BusinessError`     | `sealed interface`: `NotFound`, `BusinessRule`, `Validation`, `Conflict`, `Infrastructure` |
| `BusinessException` | `RuntimeException` que carrega um `BusinessError`              |
| `BusinessEvent`     | Subinterface de `Message` para eventos de domínio              |

## 1. Erro de domínio

`key` é **chave de tradução** (`br.commons.locale.i18n.I18N`), não texto pronto.
`args` alimentam `String.format` do padrão no bundle `messages` (da aplicação).

```java
return Result.failure(new BusinessError.NotFound("order.not_found", orderId));
```

Texto só nasce em `render(locale)`, no locale de quem pede (request HTTP):

```java
String msg = error.render(request.locale());
error.render();      // locale do processo (L10N.locale(), propriedade br.commons.locale)
error.toConsole();   // Logger.error(render())
```

`Lifecycle.initialize()/terminate()` devolvem `Result<Void, BusinessError>`;
falha de infra vira `Infrastructure(texto)` (texto já localizado por `Errors`,
`render` devolve como está) e o `br.Application` chama `toConsole()`.

Escolha da variante = significado HTTP esperado pela camada web:

| Variante       | Uso                                              |
|----------------|--------------------------------------------------|
| `NotFound`     | Recurso inexistente                              |
| `Validation`   | Entrada inválida (formato, obrigatório)          |
| `BusinessRule` | Entrada válida, mas regra proíbe                 |
| `Conflict`     | Estado concorrente/duplicado                     |
| `Infrastructure` | Falha fora do domínio (porta, worker); texto pronto |

## 2. Exceção de domínio

Quando o fluxo não usa `Result`, lance:

```java
throw new BusinessException(new BusinessError.Conflict("order.duplicated", id));
```

`getMessage()` é renderizado em **pt-BR fixo** (log/stack trace). Resposta ao
usuário deve usar `getError().render(locale)`, nunca `getMessage()`.

## 3. Evento de domínio

```java
public record OrderPaid(UUID orderId) implements BusinessEvent {}
MessageBus.submit(new OrderPaid(id));
```

Consumidores: `@MessageListener` em `br.commons.message` (ver AGENTS.md de lá).
Listener com parâmetro `BusinessEvent` recebe todos os eventos de domínio.

## 4. Regras rápidas para agentes

1. Nunca passe texto final como `key`; crie a chave no bundle `messages`.
2. Não chame `render` dentro do caso de uso — só na borda (HTTP/view).
3. Não use `getMessage()` da `BusinessException` em resposta ao usuário.
4. Evento de domínio = `record implements BusinessEvent`, imutável.
5. Nova categoria de erro exige mexer no `sealed` e em todo `switch` sobre ele.

## 5. Checklist de revisão

- [ ] Chave existe em `messages*.properties` (root pt-BR e `_en_US`).
- [ ] Quantidade/tipo de `args` casa com os `%s`/`%d` do padrão.
- [ ] Variante de erro coerente com a tabela do §1.
- [ ] Renderização acontece com locale da requisição.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 6. Resolução de texto

`BusinessError.render` delega a `I18N.of(key, locale, args)` (`render()` sem
locale usa `I18N.of(key, args)` → `L10N.locale()`):

- Busca `ResourceBundle` `messages` (classpath da aplicação; a lib não traz
  um) com fallback padrão do JDK.
- Sem `args`, o padrão/chave volta sem `String.format`.
- Chave ausente **não lança**: `String.format` roda sobre a própria chave. Isso
  permite migração incremental (call site antigo passando prosa pt-BR como
  "chave" continua funcionando). Efeito colateral: erro de digitação na chave
  vira texto cru na tela, sem falha — teste as chaves.
- `%` literal em prosa usada como chave quebra o `String.format` com `args`.

## 7. `args` e igualdade

Variantes são `record` com `Object... args` e `equals`/`hashCode`/`toString`
explícitos por **conteúdo** (`Arrays.equals`): dois `NotFound("k", 1)` são
iguais. Ao criar nova variante, repita os três métodos.

## 8. Exceção

- `BusinessException` resolve a mensagem no construtor (custo de bundle +
  format em cada criação). Não use em laço quente como controle de fluxo.
- `LOG_LOCALE` é `pt-BR` fixo por decisão: logs e time em pt-BR.
- Mapeamento para status HTTP fica fora do pacote (não há mapper na lib) —
  ao criar variante nova, revise os `switch`/mappers da aplicação.

## 9. Pontos de extensão

| Necessidade                  | Onde mexer                                         |
|------------------------------|----------------------------------------------------|
| Nova categoria de erro       | novo `record` em `BusinessError` + mappers/switches |
| Erro com campo (validação)   | componente extra no `record` (ex.: `field`)        |
| Falhar em chave inexistente  | `I18N.of` — hoje silencioso por design             |
