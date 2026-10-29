# Estilo de Codificação

Este documento descreve o estilo de codificação do projeto. Isto é, padrão de nome de variável, de comprimento de linha, etc.

Estrutura e camadas em [ARCHITECTURE.md](ARCHITECTURE.md).

## CSTY-01

- Toda variável que pode ser final deve ser marcada com `lombok.val`
- `var` só onde o padrão exige (desconstrução em `instanceof`/`switch`: `Result.Failure<T, E>(var error)`).
- Do `lombok`, só `val`: nada de `@Getter`, `@Setter`, `@Builder`, `@Data`.

## CSTY-02

- Toda verificação de nulidade é feita com `jspecify`
- Não se cria `package-info.java`: toda classe (inclusive teste) é `@NullMarked` na própria declaração; o que pode ser
  nulo leva `@Nullable` explícito, no tipo: `private @Nullable Account parent;`.
- Ausência em leitura é `Optional`; ausência em campo ou parâmetro é `@Nullable`. Nunca `Optional` como campo ou
  parâmetro.

## CSTY-03 Erro como valor

- Falha esperada é `br.commons.Result`: `Result<T, String>` na infraestrutura (texto já localizado),
  `Result<T, BusinessError>` da aplicação para fora.
- Retorno antecipado de falha por padrão de registro, sem `get()`:

```java
val built = splits(splits);
if (built instanceof Result.Failure<List<BookSplit>, BusinessError>(var error)) return Result.failure(error);
return save(BookTransaction.of(date, description.strip(), built.getOrThrow(), null, note), null);
```

- Encadeamento com `map`/`flatMap`/`mapFailure` quando cada passo depende do anterior:
  `load().flatMap(_ -> outro.load()).mapFailure(BusinessError.Infrastructure::new)`.
- Exceção só para bug ou para regra do domínio (`BusinessException`), convertida em `Failure` no caso de uso.
- Nunca `System.out`, `printStackTrace` nem `catch` vazio: `br.commons.Logger`.

## CSTY-04 Nomes

- Código, javadoc e nomes em inglês; documentos e textos de tela em pt-BR (por chave i18n).
- Acessores fluentes, sem `get`/`set`: `type()` lê, `type(AccountType)` escreve. Parâmetro de escrita genérico se chama
  `desired`.
- Métodos com nome do que devolvem, não do que fazem: `revised(...)`, `selected(...)`, `moving(...)`; verbo
  só para comando (`post`, `transfer`, `revise`, `discard`, `close`, `load`).
- Parâmetros ignorados de lambda: `_` (`flatMap(_ -> ...)`).
- Implementação de contrato leva o infixo do adaptador, nunca sufixo `Impl`: `AccountJdbcRepository implements
  AccountRepository`.
- Constantes `UPPER_SNAKE`; ids e seletores de tela como constantes da view (`PANEL`, `FILTERED`).
- Sem abreviações (`account`, `transaction`, `period`), exceto as consagradas (`id`, `db`).

## CSTY-05 Classes

- `final` por padrão; `package-private` por padrão (só é `public` o que outra camada usa). `*Views` é package-private;
  `*Routes` é pública.
- Utilitária: `final`, construtor `private` vazio, métodos `static` (`private Rules() {}`).
- Construtor de serviço é package-private: só o módulo da camada o cria.
- Dependências como campos inicializados do `Context`, não por parâmetro de construtor:

```java
private final AccountRepository accounts = Context.get(AccountRepository.class);
private final Lock lock = Context.get(BookLock.class);
```

- Dado imutável é `record`; linha de banco é `record` package-private em arquivo próprio no pacote do adaptador
  (`BookRow.java`: `record BookRow(@Nullable String threshold) {}`).
- Sem tipo aninhado: nenhuma classe, `record`, `enum` ou `interface` dentro de outra, nem tipo top-level a mais no
  mesmo arquivo. Todo tipo fica em seu próprio arquivo, com o nome do tipo; o que seria privado vira package-private.
  Classe anônima também não: use lambda ou referência de método.
- Campo e acessores juntos, em uma linha cada, sem linha em branco entre eles:

```java
private AccountType type = AccountType.ASSET;
public final AccountType type() { return type; }
public final void type(AccountType type) { this.type = type; }
```

- Ponto de entrada sem `public` nem `args`: `static void main() { new Judas().run(); }`.

## CSTY-06 Javadoc

- Toda classe tem javadoc. Sem `package-info.java` (CSTY-02): o papel de cada pacote e de quem ele depende ficam em
  [ARCHITECTURE.md](ARCHITECTURE.md).
- Frase curta, em inglês, dizendo **o que é** ou **o que devolve**, sem "This method..." nem repetir a assinatura:
  `/** The transactions dated in the month, by date. */`.
- Regra de negócio cita a funcionalidade: `(FEAT-05)`; não copia o texto do FEATURE.md.
- Javadoc de uma linha cabe em `/** ... */` numa linha só.
- Comentário de linha só quando o código não diz o porquê.

## CSTY-07 Formatação

- Linha até 150 colunas; continuação alinhada aos parâmetros ou com 8 espaços.
- Indentação de 4 espaços, sem tab.
- `if` com retorno simples na mesma linha, sem chaves: `if (threshold == null) return Result.success();`. Corpo com
  mais de um comando sempre com chaves.
- Ternário longo quebra antes de `?` e `:`.
- Imports explícitos; curinga só para estático de DSL (`import static br.commons.lang.Html5.*;`) e para o pacote
  `model` do próprio domínio.
- Uma linha em branco entre membros (exceto campo + acessores, CSTY-05).

## CSTY-08 Tipos de valor

- Dinheiro sempre em `br.commons.money.MonetaryAmount` (ARCH-11); nunca `BigDecimal`, `double` ou `long` solto.
  Arredondamento só dentro dele (`HALF_UP`); divisão só por `split`.
- Datas de negócio em `LocalDate`/`YearMonth`; timestamps `long` em millis (`System.currentTimeMillis()`).
- Hoje: `LocalDate.now(Context.get(Clock.class))`, nunca `LocalDate.now()` sem relógio.
- Texto em minúsculas por `br.commons.Strings#lower`, nunca `toLowerCase`.
- Identidade `UUID`.

## CSTY-09 Textos

- Nenhum texto de tela ou de erro literal no código: chave `judas.*` (domínio, por `DomainErrors.rule/notFound/validation`)
  ou `forms.*` (tela, por `Ui.message`).
- Cada chave de erro tem javadoc com os argumentos: `/** Args: account name, parent name. ... */`.
- Toda chave nos dois bundles (`pt_BR`, `en_US`).
- Chave de configuração com prefixo `br.commons.`; nome do cliente nunca aparece no código.

## CSTY-10 Testes

- JUnit 5, classe package-private `@NullMarked` com javadoc dizendo o cenário.
- Nome do método diz o comportamento em camelCase inglês: `closedPeriodRejectsTheOldDate()`.
- Javadoc do teste cita o critério de aceitação que cobre: `/** FEAT-05.1 */`.
- Datas fixas (`private static final LocalDate TODAY`) e `Clock.fixed`; nunca a data real.
- Teste de aplicação monta o `Context` como `Judas` monta (`Context.clear()` + módulos), não com dublê.
- Asserções de `Result` pelo resultado concreto (`getOrThrow()` em sucesso esperado, padrão `Failure(var error)` na
  falha esperada).

## CSTY-11 Qualidade

- Compila sem aviso no Error Prone e no NullAway; nenhum warning do IntelliJ nos arquivos tocados.
- `mvn verify` (PMD, CPD, JaCoCo) passa antes do commit.
- Commit em Conventional Commits com escopo: `feat(judas): ...`, `fix(judas): ...`.

## CSTY-NN
