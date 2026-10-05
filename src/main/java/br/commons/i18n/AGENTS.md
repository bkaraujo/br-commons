# br.commons.i18n — textos traduzidos e formatação por locale

Dois mecanismos: bundles **tipados** (`Bundles` + interface) para texto de
interface, e `Messages` (chave → texto) para erros de domínio.

| Tipo             | Papel                                                         |
|------------------|---------------------------------------------------------------|
| `Bundles`        | Cria proxy de interface sobre `ResourceBundle`; valida no `of` |
| `Text`           | Texto visível: `message` (do bundle) ou `data` (dado do usuário) |
| `Formats`        | Formatação por locale: BRL, número, %, datas (CLDR)            |
| `Messages`       | `of(key, locale, args)` — bundle `messages`, `String.format`   |
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
- Placeholders por **nome do parâmetro** (`{name}`) — compile com
  `-parameters`.
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

Views aceitam `Text` onde há texto visível — separa por tipo o que é
traduzível do que é dado.

## 4. `Messages` (erros de domínio)

```java
Messages.of("order.not_found", locale, id);   // bundle "messages", String.format
```

Chave ausente não lança: formata a própria chave (ver `br.commons.business`).

## 5. Regras rápidas para agentes

1. Texto de interface novo → método na interface + chave em **todos** os
   locales do bundle.
2. Nunca monte texto visível com concatenação; use placeholder.
3. Dinheiro é sempre BRL; implemente `CurrencyAmount` no tipo de domínio.
4. Data em view: `LocalDate`/`DayMonth`/`YearMonth` como argumento, não string
   pré-formatada.
5. Dado do usuário entra como `Text.data`, nunca como chave.

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

- Padrões CLDR via `ofLocalizedPattern` (`yMd`, `yMMMM`, `yMMM`) — nada de
  padrão fixo no código.
- `dayMonth` força dia com 2 dígitos substituindo `d` isolado por `dd`.
- `brl` cria `NumberFormat` a cada chamada (não thread-safe para cachear).
  Instância de `Formats` é imutável e thread-safe.
- `percent`: no máximo 1 casa decimal.
- `monthName`: standalone, capitalizado ("Setembro").

## 10. `Messages` × `Bundles`

| Aspecto          | `Messages`                 | `Bundles`                    |
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
