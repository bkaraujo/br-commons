# br.commons.business — erros e eventos de domínio

Tipos que o caso de uso usa para falar de regra de negócio sem depender de HTTP,
banco ou idioma.

| Tipo                | Papel                                                          |
|---------------------|----------------------------------------------------------------|
| `BusinessError`     | `sealed interface`: `NotFound`, `BusinessRule`, `Validation`, `Conflict` |
| `BusinessException` | `RuntimeException` que carrega um `BusinessError`              |
| `BusinessEvent`     | Subinterface de `Message` para eventos de domínio              |

## 1. Erro de domínio

`key` é **chave de tradução** (`br.commons.i18n.Messages`), não texto pronto.
`args` alimentam `String.format` do padrão no bundle `messages`.

```java
return Result.failure(new BusinessError.NotFound("order.not_found", orderId));
```

Texto só nasce em `render(locale)`, no locale de quem pede (request HTTP):

```java
String msg = error.render(request.locale());
```

Escolha da variante = significado HTTP esperado pela camada web:

| Variante       | Uso                                              |
|----------------|--------------------------------------------------|
| `NotFound`     | Recurso inexistente                              |
| `Validation`   | Entrada inválida (formato, obrigatório)          |
| `BusinessRule` | Entrada válida, mas regra proíbe                 |
| `Conflict`     | Estado concorrente/duplicado                     |

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

`BusinessError.render` delega a `Messages.of(key, locale, args)`:

- Busca `ResourceBundle` `messages` com fallback padrão do JDK.
- Chave ausente **não lança**: `String.format` roda sobre a própria chave. Isso
  permite migração incremental (call site antigo passando prosa pt-BR como
  "chave" continua funcionando). Efeito colateral: erro de digitação na chave
  vira texto cru na tela, sem falha — teste as chaves.
- `%` literal em prosa usada como chave quebra o `String.format` com `args`.

## 7. `args` e igualdade

Variantes são `record` com `Object... args`: `equals`/`hashCode` do record
comparam o **array por referência**. Dois `NotFound("k", 1)` não são iguais.
Em teste, compare `key()` e `Arrays.equals(args())`, não o erro inteiro.

## 8. Exceção

- `BusinessException` resolve a mensagem no construtor (custo de bundle +
  format em cada criação). Não use em laço quente como controle de fluxo.
- `LOG_LOCALE` é `pt-BR` fixo por decisão: logs e time em pt-BR.
- Mapeamento para status HTTP fica fora do pacote (`DomainExceptionMapper`,
  citado no javadoc) — ao criar variante nova, atualize esse mapper.

## 9. Pontos de extensão

| Necessidade                  | Onde mexer                                         |
|------------------------------|----------------------------------------------------|
| Nova categoria de erro       | novo `record` em `BusinessError` + mappers/switches |
| Erro com campo (validação)   | componente extra no `record` (ex.: `field`)        |
| Falhar em chave inexistente  | `Messages.of` — hoje silencioso por design         |
