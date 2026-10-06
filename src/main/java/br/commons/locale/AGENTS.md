# br.commons.locale — i18n (mecanismo) e l10n (conteúdo por locale)

**i18n** (internacionalização): código pronto para qualquer locale sem mudar
código — texto fora do código, formatação por locale, placeholders, locale em
tempo de execução. **l10n** (localização): o conteúdo de cada locale —
traduções e a lista do que existe. Um pacote para cada:

| Pacote / tipo           | Papel                                                        |
|-------------------------|--------------------------------------------------------------|
| `i18n.Bundles`          | Proxy de interface sobre `ResourceBundle`; valida no `of`    |
| `i18n.Text`             | Texto visível: `message` (bundle) ou `data` (dado do usuário) |
| `i18n.Formats`          | Formata por locale (CLDR): dinheiro, número, %, datas        |
| `i18n.I18N`             | `locale()` do processo; `of(key, [locale,] args)` (bundle `messages`) |
| `i18n.Errors`           | `get()`/`get(locale)` → `ErrorMessages`; `httpStatus(code)`  |
| `i18n.ErrorMessages`    | Interface tipada: um método por erro da lib                  |
| `i18n.CurrencyAmount`   | Dinheiro do domínio: `amount()` + `currency()` (padrão BRL)  |
| `i18n.DayMonth`         | `LocalDate` formatado como "12 de set."                      |
| `l10n.Locales`          | `PT_BR`, `EN_US`, `SUPPORTED`, `supported(locale)`           |
| `l10n/*.properties`     | `errors_<locale>`, `http_status_<locale>` (classpath)        |

Regra de dependência: `l10n` não conhece `i18n`; `i18n` lê `l10n`.

## 1. Bundle tipado (i18n)

```java
public interface UiText {
    Text greeting(String name, LocalDate today);   // greeting=Olá, {name}! Hoje é {today}.
    Text total(CurrencyAmount amount, YearMonth month);
    Text color(Color c);                            // color_RED=Vermelho, color_BLUE=Azul
}
UiText ui = Bundles.of(UiText.class, "messages/ui", Locales.PT_BR);
ui.greeting("Maria", LocalDate.now()).value();     // "Olá, Maria! Hoje é 05/10/2026."
```

- Um método por chave; retorno **sempre** `Text`. Placeholders por **nome do
  parâmetro** (`{name}`) — compile com `-parameters`.
- Método com único parâmetro enum lê `<metodo>_<CONSTANTE>`.
- Apóstrofo é literal (`D'Ávila`); não é `MessageFormat`. `default` não é validado.

## 2. Formatação de argumentos (por tipo, pt-BR)

| Argumento        | Saída                | Argumento   | Saída              |
|------------------|----------------------|-------------|--------------------|
| `Text`           | `value()` como está  | `YearMonth` | `setembro de 2026` |
| `CurrencyAmount` | `R$ 1.234,56` (na moeda do valor) | `Number` | `1.200`  |
| `LocalDate`      | `30/09/2026`         | outro       | `toString()`       |
| `DayMonth`       | `30 de set.`         | `null`      | vazio              |

Direto: `new Formats(locale).money(valor, Currency.getInstance("USD"))` → `US$ 1.234,50`.

## 3. `Text`, `I18N` e erros da lib

```java
Text.message("Salvar");                   // interface (normalmente via bundle)
Text.data(categoria.nome());              // dado do usuário/banco, como está
I18N.of("order.not_found", locale, id);   // bundle "messages" da aplicação
I18N.locale();                            // br.commons.locale → Locales.supported
Errors.get().routerFrozen(method, template).value();   // erro da lib, locale do processo
```

Chave ausente em `I18N.of` não lança: formata a própria chave. Todo erro da lib
(`Result.failure`, exceção, `ProblemDetail`) sai de `ErrorMessages` +
`l10n/errors_<locale>.properties`; status HTTP de `l10n/http_status_<locale>`.

## 4. Decisões e pendências

- **Log em pt-BR fixo** (`BusinessException.LOG_LOCALE`): decisão — log é
  para a equipe, não para o usuário.
- **Locale dos erros da lib é o do processo** (`Errors.get()`), não o da
  requisição; só `BusinessError.render(locale)` resolve por requisição.
  Pendente: `Accept-Language` → `Errors.get(locale)` em `web`/`http`.
- Moeda é do valor (`CurrencyAmount.currency()`), não do locale.

## 5. Regras rápidas para agentes

1. Texto de interface novo → método na interface + chave em **todos** os locales.
2. Erro novo na lib → método em `ErrorMessages` + chave em todo
   `l10n/errors_<locale>.properties`; nunca literal. Número sem milhar
   (porta, ano) → parâmetro `String`.
3. Locale novo → `.properties` de todo bundle de `l10n` + `Locales.SUPPORTED`
   e `supported()`; rode `ErrorsTest`.
4. Nunca monte texto visível com concatenação; use placeholder.
5. Data/dinheiro em view: passe `LocalDate`/`YearMonth`/`CurrencyAmount`, nunca string pronta.
6. Dado do usuário entra como `Text.data`, nunca como chave.
7. Padrão de data fixo no código (`"dd/MM"`) é bug de i18n: use `Formats` ou CLDR.

## 6. Checklist de revisão

- [ ] `Bundles.check(type, base, locale)` vazio para cada `Locales.SUPPORTED`.
- [ ] Nenhuma chave órfã nos `.properties`; build com `-parameters`.
- [ ] `Text.data` só para conteúdo vindo de dados.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 7. Validação (`Bundles.check`)

Ao criar (`of`) e em testes de completude. Problemas reportados:

- `missing key <k>` — método sem chave (ou `<m>_<CONST>` ausente).
- `orphan key <k>` — chave no bundle sem método.
- `<m> must return Text`.
- `<m>: {x} is not a parameter` — placeholder sem parâmetro homônimo.
- `<m>: compile with -parameters`.

`of` lança `IllegalStateException` com a lista → bundle incompleto derruba a
aplicação na inicialização (intencional: falha cedo).

Carga usa `Control.getNoFallbackControl(FORMAT_PROPERTIES)`: **sem fallback**
para o locale padrão da JVM — `messages/ui_en_US.properties` ausente falha,
em vez de cair silenciosamente no root. O arquivo root (`messages/ui.properties`)
ainda é o pai na cadeia de candidatos.

## 8. Proxy

- `Handler` (record) resolve: `equals` por identidade, `hashCode` identidade,
  `toString` = `Bundle(base, locale)`.
- Substituição em passagem única (`Matcher.appendReplacement` +
  `quoteReplacement`): valor com `{outro}`, `$` ou `\\` entra literal.
  Placeholder sem parâmetro de mesmo nome fica no texto.
- Parâmetro sem placeholder no template é aceito (não reportado).
- Cada chamada formata via `Formats` do locale do bundle; um proxy por locale.

## 9. `Formats`

- Padrões CLDR via `ofLocalizedPattern` (`yMd`, `yMMMM`, `yMMM`) e
  `getLocalizedDateTimePattern("MMMd")` — nada de padrão fixo no código.
- `dayMonth` força dia com 2 dígitos substituindo `d` isolado por `dd`.
- `money`/`brl` criam `NumberFormat` a cada chamada (não thread-safe para cachear).
  Instância de `Formats` é imutável e thread-safe.
- `percent`: no máximo 1 casa decimal.
- `monthName`: standalone, capitalizado ("Setembro").
- `Errors` guarda um proxy `ErrorMessages` por locale suportado (cache);
  `Bundles.of` lança mensagem literal (não traduzida) para não recursar.

## 10. `I18N` × `Bundles`

| Aspecto          | `I18N`                     | `Bundles`                    |
|------------------|----------------------------|------------------------------|
| Chave            | string livre               | método da interface          |
| Validação        | nenhuma; ausente = chave   | completa, na criação         |
| Placeholders     | `String.format` (`%s`)     | `{nome}` tipado              |
| Fallback locale  | padrão do JDK              | sem fallback                 |
| Uso              | `BusinessError.render`     | views/UI                     |

## 11. Extensão

- Novo tipo formatado: adicionar `case` em `Formats.argument` (ordem importa:
  `Text`/`CurrencyAmount` antes de `Number`).
- Outra moeda: o tipo do domínio sobrescreve `CurrencyAmount.currency()`;
  `Formats.argument` já usa `money(amount, currency)`.
