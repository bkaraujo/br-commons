# br.commons.i18n — textos traduzidos e formatação por locale

Dois mecanismos: bundles **tipados** (`Bundles` + interface) para texto de
interface, e `I18N` (chave → texto) para erros de domínio.

| Tipo             | Papel                                                         |
|------------------|---------------------------------------------------------------|
| `Bundles`        | Cria proxy de interface sobre `ResourceBundle`; valida no `of` |
| `Text`           | Texto visível: `message` (do bundle) ou `data` (dado do usuário) |
| `Formats`        | Formatação por locale: BRL, número, %, datas (CLDR)            |
| `I18N`           | `of(key, [locale,] args)` — bundle `messages`, `String.format`; `locale()` |
| `Errors`         | `get()`/`get(locale)` → `ErrorMessages` da lib; `httpStatus(code)` |
| `ErrorMessages`  | Bundle tipado com toda mensagem de erro da lib                 |
| `CurrencyAmount` | Interface do tipo dinheiro do domínio (`amount()`)             |
| `DayMonth`       | Wrapper de `LocalDate` formatado como "12 de set."             |

## 1. Bundle tipado

```java
public interface UiText {
    Text greeting(String name, LocalDate today);   // greeting=Olá, {name}! Hoje é {today}.
    Text total(CurrencyAmount amount, YearMonth month);
    Text color(Color c);                            // color_RED=Vermelho, color_BLUE=Azul
}

UiText ui = Bundles.of(UiText.class, "messages/ui", Locale.of("pt", "BR"));
ui.greeting("Maria", LocalDate.now()).value();     // "Olá, Maria! Hoje é 05/10/2026."
```

- Um método por chave; retorno **sempre** `Text`.
- Placeholders por **nome do parâmetro** (`{name}`) — compile com `-parameters`.
- Método com único parâmetro enum lê `<metodo>_<CONSTANTE>`.
- Apóstrofo é literal (`D'Ávila`); não é `MessageFormat`.
- Métodos `default` são ignorados pela validação (podem compor outros).

## 2. Formatação de argumentos (por tipo)

| Tipo do argumento | Saída pt-BR           |
|-------------------|-----------------------|
| `Text`            | `value()` como está   |
| `CurrencyAmount`  | `R$ 1.234,56`         |
| `LocalDate`       | `30/09/2026`          |
| `DayMonth`        | `30 de set.`          |
| `YearMonth`       | `setembro de 2026`    |
| `Number`          | `1.200`               |
| outro             | `toString()`          |
| `null`            | vazio                 |

Uso direto: `new Formats(locale).percent(new BigDecimal("0.82"))` → `82%`.

## 3. `Text`: interface × dado

```java
Text.message("Salvar");          // texto de interface (normalmente via bundle)
Text.data(categoria.nome());     // dado do usuário/banco, exibido como está
```

Views aceitam `Text` onde há texto visível — separa traduzível de dado.

## 4. `I18N` (erros de domínio)

```java
I18N.of("order.not_found", locale, id);   // bundle "messages", String.format
I18N.of("order.not_found", id);           // locale = I18N.locale()
I18N.locale();                            // de `br.commons.locale`: PT_BR ou EN_US
```

Chave ausente não lança: formata a própria chave (ver `br.commons.business`).

## 4.1 Erros da própria lib (`Errors`)

`Result.failure(Errors.get().routerFrozen(method, template).value());`

Todo `Result.failure`/exceção/`ProblemDetail` do `br.commons` usa
`ErrorMessages` (`br/commons/i18n/errors_{pt_BR,en_US}.properties`).
Locale: system property `br.commons.locale` (`I18N.LOCALE_PROPERTY`, padrão
`pt-BR`; outro idioma → en-US, via `I18N.supported`). Descrição de `HTTPStatus`:
`Errors.httpStatus(code)` ← `http_status_*.properties` (código ausente = o número).

## 5. Regras rápidas para agentes

1. Texto de interface novo → método na interface + chave em **todos** os
   locales do bundle.
2. Nunca monte texto visível com concatenação; use placeholder.
3. Dinheiro é sempre BRL; implemente `CurrencyAmount` no tipo de domínio.
4. Data em view: `LocalDate`/`DayMonth`/`YearMonth` como argumento, nunca string.
5. Dado do usuário entra como `Text.data`, nunca como chave.
6. Erro novo na lib → método em `ErrorMessages` + chave nos dois locales;
   nunca literal. Número sem milhar (porta, ano) → parâmetro `String`.

## 6. Checklist de revisão

- [ ] `Bundles.check(type, base, locale)` vazio para cada locale suportado.
- [ ] Nenhuma chave órfã no `.properties`.
- [ ] Build com `-parameters` (placeholders dependem disso).
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
- `brl` cria `NumberFormat` a cada chamada (não thread-safe para cachear).
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
- Outra moeda: hoje fixa em BRL por decisão; parametrizar `Formats` exigiria
  mudar `CurrencyAmount` para carregar `Currency`.
