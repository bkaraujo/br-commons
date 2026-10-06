# br.commons.chrono — tempo e datas

Utilitários estáticos de relógio e constantes de data.

| Tipo    | Papel                                                         |
|---------|---------------------------------------------------------------|
| `Time`  | Relógio: `millis`, `nanos`, `seconds`, `now`, `yyyyMMdd`      |
| `Dates` | Constantes: `MILLIS_IN_DAY`, padrão/formatter `dd/MM/yyyy`, meses (`MONTHS_PTBR`, `MONTHS_ENUS`, `LOCALIZED`) |

## 1. Relógio

```java
long t0 = Time.nanos();           // medir duração (monotônico)
long ts = Time.millis();          // epoch ms (wall clock)
double s = Time.seconds();        // epoch em segundos, double
LocalDateTime agora = Time.now(); // fuso do sistema
String hoje = Time.yyyyMMdd();    // "2026-10-05"
```

- Duração → `nanos()`. Instante → `millis()`/`now()`.
- Use `Time` em vez de `System.*` direto: ponto único para trocar o relógio.

## 2. Datas

```java
Matcher m = Dates.PATTERN_DDMMYYYY.matcher(texto);     // captura "05/10/2026"
LocalDate d = LocalDate.parse(m.group(1), Dates.FORMATTER_DDMMYYYY);
int mes = Dates.MONTHS_PTBR.get("OUTUBRO");            // 10
int m2 = Dates.LOCALIZED.get(Locale.of("en", "US")).get("MAY");  // 5 (só pt-BR e en-US)
long dias = intervaloMs / Dates.MILLIS_IN_DAY;
```

Chaves dos mapas de meses são **maiúsculas**; normalize antes
(`br.commons.text.TextNormalizer.normalize` também remove acento — cuidado:
`MARÇO` vira `MARCO` e não casa).

## 3. Regras rápidas para agentes

1. Formatação para usuário final: use `br.commons.locale.i18n.Formats`, não `Dates`.
2. Não use `Time.now()` para persistir instante; prefira `Instant`/UTC.
3. Não meça duração com `millis()`.

## 4. Checklist de revisão

- [ ] Duração medida com `nanos()`.
- [ ] Busca em `MONTHS_*` usa texto em maiúsculas e com acento correto.
- [ ] Datas exibidas passam por `Formats` com locale.

---

<!-- Fim da seção de uso (até a linha 100). Abaixo, implementação avançada. -->

# Implementação avançada

## 5. Armadilhas conhecidas

- `MILLIS_IN_DAY` ignora horário de verão; para dias de calendário use
  `ChronoUnit.DAYS.between(LocalDate, LocalDate)`.
- `Time` não tem construtor privado (diferente de `Dates`); é `abstract` só
  para impedir instância direta.
- `Time.now()` e `yyyyMMdd()` dependem de `ZoneId.systemDefault()` — resultado
  muda conforme o fuso da máquina/container.
- `Time.seconds()` perde precisão sub-ms (double sobre `currentTimeMillis`).

## 6. Testabilidade

Não há relógio injetável. Código que precisa de tempo determinístico em teste
deve receber `Clock`/`LongSupplier` por parâmetro (como `Execution.time` faz
com `LongSupplier clock`). Evolução natural: `Time.clock(Clock)` estático com
reset para testes.
