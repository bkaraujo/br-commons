# br.money — pendências

Itens levantados na revisão de código de 2026-10-21 (commits `8fe1992..3338e49`) e deixados para depois. Os
itens 1–9 dessa revisão já foram corrigidos (`e02e934`, `473af90`, `3338e49`). Ao fazer um item, remova-o daqui.

## 10. Query string crua no log de INFO

- **Onde:** `br.commons.web.filter.RequestLoggingFilter` (usa `request.uri()` com a query).
- **Problema:** dados de formulário enviados por GET vão para o log. O htmx do formulário de splits
  (`/statement/transactions/split`) manda descrição, valores e contas na query; outras apps sobre `br.commons` que
  recebam `?token=` passam a vazá-lo.
- **Sugestão:** logar só o caminho (`uri().getRawPath()`), ou a query com os valores mascarados.

## 11. Tipo de conta do GnuCash desconhecido derruba a inicialização

- **Onde:** `web/seed/GnuCashSeed` (`AccountType.valueOf(account.type())`).
- **Problema:** tipos válidos do GnuCash que não existem no enum (CURRENCY, CHECKING, SAVINGS, MONEYMRKT,
  CREDITLINE) lançam `IllegalArgumentException`; isso e os erros de IO/XML saem como exceção de
  `WebModule.initialize` em vez de `Result`, e o servidor web não sobe.
- **Sugestão:** mapear os tipos legados para os do enum (CHECKING/SAVINGS/MONEYMRKT → BANK, CREDITLINE → CREDIT;
  CURRENCY conforme INTENT §4) e devolver `Result.failure` para o resto.

## 12. Sobra de depuração no `InMemoryStorage`

- **Onde:** `br.commons.persistence.inmemory.InMemoryStorage` (`read` e `delete`).
- **Problema:** `read` loga em INFO (o resto loga em trace) e `delete` loga o mapa inteiro de campos (`byte[]`) no
  lugar da chave — log inútil que cresce com o arquivo.
- **Sugestão:** `read` em trace e `delete` logando `jsonKey`.

## 13. Árvore de contas recalculada várias vezes por requisição

- **Onde:** `web/statement/StatementRoutes` (`chosen`, `month`) e `web/book/Book` (`balances`, `inNature`, `tree`).
- **Problema:** `chosen` monta o `fallback` antes de olhar o parâmetro e chama `book.financial()` duas vezes (cada
  uma faz `tree()`, O(n²) em `findChildren`); `month` chama `book.month()`, que ordena todas as transações, mesmo
  com `?month=` presente; `inNature` refaz `subtree` para cada conta. Com o livro GnuCash real, cada clique no
  extrato reconstrói a árvore várias vezes.
- **Sugestão:** `orElseGet` nos fallbacks e calcular `tree()` (e os filhos de cada conta) uma vez por requisição.

## 14. Código reimplementado

- **Onde:**
  - `Book.value` repete `BookTransaction.value(splits)`.
  - `Book.subtree` repete `Balances.subtree`.
  - A conta de sinais de uma transferência está em três lugares: `TransactionService.transfer`, `Book.revise` e
    `Seed.move`.
  - `br.commons` `HTTPServer` faz `parseQuery` e o construtor de `HTTPServerRequest` faz de novo.
- **Problema:** mudar a regra num ponto deixa os outros divergentes (tela e serviço com valores diferentes), e cada
  requisição decodifica a query duas vezes.
- **Sugestão:** um só dono para cada regra (serviço/domínio), reaproveitado pela tela; a query decodificada uma vez.
