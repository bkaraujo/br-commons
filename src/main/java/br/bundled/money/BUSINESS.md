# br.money — Regras de negócio

> Extraídas do código em 2026-10-23. O produto continua definido em [INTENT.md](INTENT.md) (§6 é a fonte das regras
> contábeis); este documento diz **o que o código faz hoje**. Onde o código diverge do INTENT, vale o código e a
> divergência está em §12. Navegação e interface: [UI.md](UI.md). Arquitetura: [AGENTS.md](AGENTS.md).
>
> Cada regra tem um id (`RN-xx`) e a **Fonte** onde é aplicada. Regras sem o selo 🕒 estão implementadas; as
> marcadas 🕒 são intenção do INTENT ainda sem código.

## 1. Conceitos

| Termo | O que é | Onde |
|-------|---------|------|
| Livro | Contas + transações + a data-limite (`Book.threshold`) | `model.Book`, `BookService` |
| Conta | Nó da árvore do plano de contas; tem tipo e, fora do topo, uma conta-mãe | `model.Account` |
| Transação | Evento com data, descrição e ≥ 2 splits | `model.Transaction`, `BookTransaction` |
| Split | Valor de uma transação numa conta, na natureza dela | `model.TransactionSplit`, `BookSplit` |
| Data da transação | `Transaction.date()`: quando ocorre nas contas. Base de saldos, relatórios, extratos e data-limite. Compra no cartão = data da compra; pagamento da fatura = data do pagamento (INTENT §5) | `Transaction` |
| Data da compra | `Transaction.purchased()`: dia da compra de uma compra de cartão lançada em outra data (ex.: o vencimento da fatura). Opcional; só informa, não muda saldos nem relatórios | `Transaction`, `GnuCashSeed` |
| Data de criação | `createdAt`/`updatedAt`: milissegundos desde o epoch (`Auditable`) | `Auditable` |
| Razão / extrato mensal | Transações de uma conta em um mês, com saldo corrente | `web.book.Book#ledger` |
| Conta financeira | Onde o dinheiro está (RN-05) | `Account#financial` |
| Conta contábil | Categorias, PL e contas técnicas | `Account#financial` |

## 2. Sinal, natureza e valores

**RN-01 — Convenção de sinal.** O valor do split é do ponto de vista do tipo da conta: positivo aumenta o saldo
segundo a natureza dela, negativo diminui. Saldo = Σ valores dos splits, sem conversão. Fonte: `TransactionSplit`.

**RN-02 — Natureza por tipo.** `debit` = +1 (devedora) ou −1 (credora); `credit` é o oposto.

| Tipo | Classe | Natureza | `debit` |
|------|--------|----------|---------|
| BANK, CASH, STOCK, MUTUAL | financeira | devedora | +1 |
| CREDIT (cartão) | financeira | credora | −1 |
| ASSET, RECEIVABLE | contábil | devedora | +1 |
| EXPENSE | contábil | devedora | +1 |
| LIABILITY, PAYABLE | contábil | credora | −1 |
| EQUITY, INCOME, ROOT, TRADING | contábil | credora | −1 |

Fonte: `AccountType`.

**RN-03 — Transação balanceada.** `debitAmount` do split = `valor × debit` do tipo da conta (o valor GnuCash). Toda
transação precisa de Σ `debitAmount` = 0. Fonte: `Transaction#balanced`, `#validate`.

**RN-04 — Conversão de natureza.** Para levar um valor da natureza de uma conta à de outra: `valor × debit(origem) ×
debit(destino)`. É o que somam os saldos agregados, o razão, os relatórios e a exclusão de conta com lançamentos.
Fonte: `Balances`, `web.book.Book`, `AccountService#move`.

**RN-05 — Classificação financeira × contábil.** Derivada do tipo, sem campo próprio: BANK, CASH, CREDIT, STOCK e
MUTUAL são financeiras; o resto é contábil. As telas só oferecem as financeiras que não são STOCK nem MUTUAL
(`Book#moneyAccounts`: "Contas" = não-cartão, "Cartões" = CREDIT). Fonte: `Account#financial`, `Book#moneyAccounts`.

**RN-06 — Valores.** `BigDecimal`, 2 casas, BRL (moeda única). Mais de 2 casas é rejeitado (`money.amount.scale`);
zeros à direita não contam (`10,500` vale). Splits de valor zero são descartados antes de validar. Fonte:
`TransactionService#splits`.

**RN-07 — Identidade e auditoria.** Conta, transação e split têm `UUID`; os timestamps são `long` em milissegundos.

## 3. Plano de contas

**RN-10 — Topo fixo.** As contas de topo (Ativo, Passivo, Patrimônio líquido, Receitas, Despesas) não são criadas,
alteradas nem removidas; toda conta nova precisa de conta-mãe (`money.account.top_level`). Fonte: `AccountService`.

**RN-11 — Família da mãe.** Uma subconta é da família da mãe. Famílias: ativo (BANK, CASH, STOCK, MUTUAL, ASSET,
RECEIVABLE), passivo (CREDIT, LIABILITY, PAYABLE) e, cada um a sua, EQUITY, INCOME, EXPENSE, ROOT e TRADING
(`money.account.family`). Fonte: `AccountType#family`, `AccountService#parent`.

**RN-12 — Sem ciclo.** A mãe nunca é a própria conta nem uma descendente dela (`money.account.parent`).

**RN-13 — Nome.** Obrigatório (`money.account.name`). Na tela: até 60 caracteres e **único entre irmãs, sem
diferenciar caixa**; essa unicidade é da tela (`AccountFields`), o serviço não a confere.

**RN-14 — Tipo fixo.** O tipo só muda enquanto a conta não tem lançamentos nem subcontas (`money.account.type_fixed`).
A tela mostra o tipo travado nesse caso.

**RN-15 — Tipos oferecidos na tela.** BANK, CREDIT, CASH, ASSET, LIABILITY, EQUITY, INCOME, EXPENSE. Fonte:
`AccountFields.TYPES`.

**RN-16 — Saldo inicial.** Só na criação e, na tela, só para BANK, CASH e CREDIT. O valor está na natureza da conta
(dinheiro no banco, dívida no cartão). Gera a transação **"Saldo inicial"**, datada de **hoje**, contra a conta de PL
de abertura, com contrapartida `−valor × debit(conta) × debit(PL)`:

1. conta EQUITY cujo nome, sem acento e em minúsculas, é "saldo(s) de abertura", "saldo(s) inicial(is)" ou "opening
   balance(s)";
2. senão, a única subconta do PL de topo;
3. senão, o próprio PL de topo; sem PL, recusa (`money.account.opening_equity`).

Hoje precisa estar no período aberto (RN-40). Se a transação não puder ser gravada, a conta criada é desfeita.
Fonte: `AccountService#create`.

**RN-17 — Exclusão.**
- Conta com subcontas não é excluída (`money.account.children`).
- Sem lançamentos: exclui direto.
- Com lançamentos: exige uma conta de destino, que não seja o topo nem a própria conta (`money.account.target`); a
  família e a natureza do destino não são conferidas (os saldos de um banco podem ir para uma despesa). Cada
  split é convertido à natureza do destino (RN-04) e somado ao split que o destino já tiver na transação; o split
  zerado some; a identidade dos splits é mantida; a transação que ficar com menos de 2 splits (uma transferência entre
  as duas contas) é removida. Os saldos são recalculados a partir do mês mais antigo afetado.
- Lançamento a mover em período fechado impede a exclusão (RN-40); a tela nem oferece o formulário.

Fonte: `AccountService#remove`, `#move`, `#merged`.

**RN-18 — Plano pessoal BR (F8).** "Novo livro vazio" descarta contas, transações e saldos e cria o plano de
`plano-pessoal.txt` (62 linhas): Ativo (Contas bancárias, Carteira = CASH, Investimentos), Passivo (Cartões de crédito,
Empréstimos e financiamentos), Patrimônio líquido (Saldos de abertura), Receitas e Despesas com categorias.
Bancos e cartões o usuário cria depois, com saldo inicial. Sem data-limite. Fonte: `BookService#startEmpty`,
`ChartTemplate`.

## 4. Transações

**RN-20 — Mínimos.** Pelo menos 2 splits (depois de descartar os zerados) e balanceada (`money.transaction.splits`,
`money.transaction.unbalanced`). A conta de cada split tem de existir (`money.account.not_found`).

**RN-21 — Descrição.** Obrigatória, aparada (`money.transaction.description`). Na tela: até 80 caracteres.

**RN-22 — Transferência simples.** `valor > 0` (`money.amount.positive`): entra no destino e sai da origem, cada split na
natureza da própria conta (`TransactionService#moving`: `debited` no destino, `credited` na origem). Uma despesa paga
no cartão é "do cartão para a despesa". Na tela, conferido no servidor: origem ≠ destino, ambas abaixo do topo, valor
≥ 0,01 e múltiplo de 0,01 (`Validation.check` aplica o `min` e o `step` do campo). Fonte:
`TransactionService#transfer`, `StatementFields`.

**RN-23 — Lançamento rápido.** Categoria = conta INCOME ou EXPENSE; conta = financeira. A lista oferece só as
categorias **sem subcontas** (`Book#categories`) e as contas de `Book#moneyAccounts` (sem STOCK nem MUTUAL, RN-05); o
servidor confere apenas o tipo (`Book::category`, `Account::financial`), então aceita um envio fora dessas listas.
Despesa: a conta paga a categoria. Receita: a categoria paga a conta. Data padrão: hoje. Não há modo "Transferência"
no lançamento rápido: transferências entre contas financeiras (ex.: pagamento de fatura) usam o lançamento
simples/completo do extrato. Fonte: `Book#quick`, `Book#categories`, `EntryFields`.

**RN-24 — Lançamento completo (splits).** Linhas "conta + Entra | Sai": exatamente um dos dois, `> 0`, até 2 casas;
conta abaixo do topo; pelo menos 2 linhas preenchidas; Σ Entra = Σ Sai; linha em branco é ignorada. O split é gravado
em débito × `debit` do tipo (RN-02). Fonte: `SplitFields#read`.

**RN-25 — Transação simples.** Exatamente 2 splits, um que debita e outro que credita (`Book#simple`). Qualquer outra
(ex.: contracheque com descontos) só se edita no formulário de splits.

**RN-26 — Revisão.** Reescreve a transação no lugar: mesma identidade, mesma criação; cada split de uma conta que já
estava na transação mantém identidade e criação (o GUID do round-trip GnuCash); split em conta nova é novo;
`updatedAt` = agora. Se a data muda numa transação **não parcelada**, a data da compra (`purchased`) é descartada; uma parcela
(`installmentTotal > 1`) mantém todos os seus detalhes, `purchased` inclusive.
Fonte: `TransactionService#revise`, `#keeping`, `BookTransaction#revised`.

**RN-27 — Ordem.** Transações se ordenam por data, depois `createdAt`, depois id. Fonte:
`StorageTransactionRepository.CHRONOLOGICAL`.

**RN-28 — Efeito no patrimônio.** Σ `debitAmount` dos splits **financeiros**: receita positivo, despesa negativo,
transferência zero. O rótulo é o nome das contas não financeiras, ou "Transferência" quando não há nenhuma. O valor
da transação é a soma dos débitos positivos. Fonte: `Book#effect`, `#label`, `BookTransaction#value`.

**RN-29 — Status.** `TransactionStatus` (PLANNED, CONFIRMED, CONCILIATED) existe e é gravado (padrão CONFIRMED), mas
nenhuma regra o usa. O "previsto" da tela é só `data > hoje` (UI.md).

## 5. Saldos, razão e fluxos

**RN-30 — Saldo no fim do dia D.** Conta financeira: só o próprio saldo. Conta contábil: soma da subárvore, cada
conta convertida à natureza da pedida (RN-04). O saldo próprio de uma conta em D = saldo guardado do mês anterior +
o movimento do mês até D, **inclusive**. Fonte: `Book#balance`, `#own`.

**RN-31 — Saldos guardados.** O último saldo de cada mês com movimento fica guardado (`BalanceRepository`) e é
recalculado a partir do mês mais antigo afetado a cada criação, revisão, exclusão de transação ou de conta e
importação (`Balances#refresh`, `#rebuild`). `Balances#balance(conta, mês)` lê esse saldo no fim do mês.

**RN-32 — Razão do mês.** Saldo inicial = saldo no dia anterior ao 1º do mês. Linhas: as transações do mês que tocam o
escopo (a conta se financeira; a subárvore se contábil), por data (RN-27). O valor da linha é Σ dos splits do escopo
na natureza da conta; a contraparte é o nome das outras contas; o saldo corrente acumula as linhas. Resumo: saldo
inicial, aumentos (Σ positivos), reduções (Σ negativos) e saldo final = inicial + aumentos + reduções. Fonte:
`Book#ledger`, `Ledger#summary`.

**RN-33 — Fluxo do mês.** `flow(conta, mês)` = Σ, na natureza da conta, dos splits do escopo das transações do mês.
`flows(mês)` faz o mesmo para todas as contas numa passada, somando cada subárvore antes da mãe.

**RN-34 — Fluxo de categorias.** Receitas (ou despesas) do mês das contas financeiras escolhidas: só transações que
tocam alguma delas, somando o que cai nas categorias do tipo, na natureza dele. Uma transferência entre as contas
escolhidas não soma nada. Fonte: `Book#categoryFlow`.

**RN-35 — Mês de abertura.** As telas abrem no mês da última transação datada até hoje; sem nenhuma, no mês de hoje.
Um livro importado pode parar meses atrás, e abrir num mês vazio o esconderia. Os meses listados vão do mês da
transação mais antiga (ou do mês pedido, se anterior) até 12 meses depois do mês atual (ou do pedido, se posterior).
Fonte: `Book#month`, `#firstMonth`, `Ledger#monthSelect`.

**RN-36 — Anotação.** Texto livre opcional da transação, além da descrição: aparado, em branco = nenhuma; até 500
caracteres na tela. Entra pelo lançamento rápido e se mantém nas revisões (o extrato não a altera). Fonte:
`Transaction#note`, `BookTransaction.Details`.

**RN-37 — Nome PDF.** Nome da transação como veio no extrato bancário em PDF (F5): gravado só na importação
(`BookTransaction#imported`), aparado, e **nunca editável** — revisões o preservam e nenhuma tela o altera. A
descrição passa a ser o nome dado pelo usuário: opcional quando há Nome PDF (as telas mostram o Nome PDF no lugar) e
obrigatória quando não há. Serve de chave para sugerir a mesma contrapartida a nomes repetidos (F6). Fonte:
`Transaction#pdfName`, `Transaction#label`, `BookTransaction.Details`.

## 6. Fechamento de período (F9)

**RN-40 — Data-limite.** Criar, editar ou excluir transação com data **anterior** à data-limite é rejeitado
(`money.period.closed`); a data-limite em si é aberta. Vale para a data nova **e** a antiga ao revisar e ao excluir; para
o saldo inicial de conta nova (hoje) e para os lançamentos movidos ao excluir uma conta. Sem data-limite, nada fecha.
Fonte: `model.Book#open`, `TransactionService`, `AccountService`.

**RN-41 — Reabrir.** Recuar a data-limite (ou limpá-la) é a reabertura explícita: o serviço aceita qualquer data; a
tela exige a confirmação "Confirmar reabertura". Avançar não pede confirmação. Fonte: `BookService#close`,
`ReportRoutes#close`.

**RN-42 — Importação e livro vazio** não aplicam a data-limite nem a regra de topo fixo: carregam o livro como vem,
exigindo só que cada transação balanceie (RN-50).

## 7. Cartão de crédito, fatura e parcelas

**RN-45 — Cartão.** Conta de tipo CREDIT: financeira, de natureza credora (RN-02). Uma compra aumenta a dívida
(split positivo no cartão); o pagamento da fatura é uma transferência de uma conta financeira (ex.: corrente) para o
cartão (INTENT §6).

**RN-46 — Valor do cartão na lista.** `bills(cartões, mês)` = Σ dos splits no cartão das transações **datadas no mês
civil**, compras menos estornos, na natureza do cartão. Transações em que todos os splits são de contas financeiras
(transferências, inclusive o pagamento) ficam de fora. Funciona porque, no livro atual, compra e pagamento têm a
mesma data; não é fatura por fechamento nem saldo devedor (limitação conhecida, sem decisão registrada). Fonte: `Book#bills`.

**RN-47 — Data exibida no extrato do cartão.** Na tela do extrato de uma conta que é cartão, a linha mostra a data da
compra (`purchased`) quando existe, senão a data da transação. A ordem, o mês e o saldo seguem sempre a data da
transação. Fonte: `StatementViews#shownDate`.

**RN-48 — Data da compra na importação GnuCash.** Lida da nota da transação no formato `Evento AAAA-MM-DD` (primeira
ocorrência); sem a nota, vazia. Uma data impossível na nota (ex.: `2025-02-30`) faz a carga falhar
(`money.import.malformed`). Gravada junto da transação (campo `purchased`). Fonte: `GnuCashSeed#purchase`.

**RN-49 — Parcela.** Uma linha do razão é a parcela k de N (N ≥ 2, 1 ≤ k ≤ N) quando:
1. a transação tem `installmentTotal > 1`: vale `installment`/`installmentTotal`; ou
2. a descrição traz `k/N` (`LOJA 03/10`, `3 / 10`) ou `PARC… k DE N` (qualquer palavra que comece com "parc": `PARCELA 3 DE 10`).
   Uma data completa (`05/10/2026`) não é parcela, mas uma data sem ano (`COMPRA 05/10`) vira a parcela 5/10. Vale a
   última ocorrência válida de `k/N`, a menos que haja um `PARC… k DE N` válido, que tem prioridade.

Só no caso 2 se confere 1 ≤ k ≤ N e N ≥ 2; no caso 1 os campos valem como estão. Restam `N − k` parcelas, de valor igual ao da linha; restante = valor × (N − k). Fonte: `web.book.Installment`.

**RN-49a — Parcelas do mês.** As de uma lista de contas: cada transação aparece uma vez, na primeira conta que toca,
por data. Na tela de Parcelamentos as contas são as do "Contas" (sem cartões), e as parcelas são as das contas
marcadas **mais todos os cartões**; sem conta marcada, nenhuma (a primeira visita, sem `filtered`, vem com todas marcadas). Na ordem de varredura os
cartões vêm antes das contas marcadas, e é ali que "a primeira conta que toca" é decidida. Resumo: quantidade, valor
do mês (o **módulo da soma** com sinal, |Σ|, e não a soma dos módulos), quantas são a última (`left = 0`) e o restante
(também |Σ|). Fonte: `Book#installments`, `InstallmentRoutes`.

## 8. Relatórios

**RN-60 — Balanço patrimonial.** Posição no último dia do mês pedido. Total do Ativo, do Passivo e do PL são os saldos
das contas de topo (RN-30). **Resultado acumulado** = saldo de Receitas − saldo de Despesas (de todo o livro até o dia).
Confere: Ativo = Passivo + PL + Resultado acumulado. Cada descendente é convertido à natureza do grupo, para as linhas somarem o total (a regra de família, RN-11,
impede um cartão sob o Ativo; num livro importado, que não passa por ela, ele apareceria negativo). Lista **todos** os
descendentes do grupo, indentados; uma conta que tem filhas só mostra valor se for financeira.
Fonte: `ReportViews#balanceSheet`.

**RN-61 — Demonstração do resultado (DRE).** Receitas e despesas do mês (`flow` das contas de topo) e, por conta de
segundo nível, o valor e sua fatia (barra) do total **do grupo** (receitas ou despesas). Resultado = receitas −
despesas, rotulado "Déficit do mês" se negativo, senão "Superávit do mês" (zero é superávit). **Taxa de poupança** = `resultado × 100 ÷ receitas`, arredondada a inteiro (HALF_UP), só quando receitas > 0.

**RN-62 — Evolução de gastos.** 12 meses a partir do escolhido (padrão: janeiro do ano do mês de abertura). Todas as
contas sob Receitas e Despesas, mexendo ou não, **incluindo as próprias raízes**. Cada valor mostra sua % da receita do
mês (1 casa, HALF_UP; "—" se a receita é zero) e, quando não é zero, leva ao extrato da conta naquele mês; zero aparece
como "—", sem link. Total = soma dos 12 meses, com a % sobre a receita do período. Média = soma dos meses **até o mês
atual (hoje)** ÷ quantidade desses meses (2 casas, HALF_UP); sem nenhum mês decorrido, ou zero, aparece "—".

**RN-63 — Fechamento.** Janela do Plano de contas para ver e mudar a data-limite (RN-40, RN-41; UI-57a), por mês:
fechar um mês põe a data-limite no dia 1 do mês seguinte; mês inválido é recusado.

## 9. Carga do livro

**RN-50 — Carga (ImportService).** Substitui o livro inteiro por plano + transações + data-limite. Tudo é montado e
conferido antes de remover o atual: toda transação balanceada, posição de conta válida (`money.import.account`). Se a
gravação falha no meio, o livro anterior é regravado em **melhor esforço** (o resultado da restauração é ignorado: se
ela também falhar, o livro anterior se perde). Transação não balanceada ou com menos de 2 splits na carga recai em
`money.transaction.unbalanced` / `money.transaction.splits`. Os saldos guardados são refeitos no fim.

**RN-51 — Livro GnuCash (desenvolvimento).** Com `-Dbr.commons.money.seed.gnucash=<arquivo>` o app carrega o XML
(gzip ou não) na partida; **o arquivo nunca é copiado para o repositório**. Sem a propriedade, um livro sem
lançamentos recebe o livro fictício de desenvolvimento. Regras da leitura (`GnuCashSeed`):
- Transações dos modelos de agendamento são ignoradas; splits de valor zero e transações que ficam com < 2 linhas
  também.
- Uma só moeda: os valores são tomados como estão (BRL).
- Cada transação é arredondada ao centavo e fecha a soma zero: o que o arredondamento deixa de sobra vai ao split de
  maior valor absoluto.
- Tipos legados viram os do app (`CHECKING`, `SAVINGS`, `MONEYMRKT` → BANK; `CREDITLINE` → CREDIT). Uma
  conta folha (sem filhas, com mãe) ganha o tipo financeiro pelo **nome da conta-mãe** (basta o nome casar):
  `LIABILITY` sob "cartão/cartões" → CREDIT; `ASSET` sob "corretora" → STOCK; `ASSET` sob "conta", "banc", "benefício"
  ou "carteira" → BANK. Qualquer outra combinação mantém o tipo do GnuCash.
- Conta de tipo sem equivalente (ex.: `CURRENCY`) sai com suas subcontas se nada se move nelas; se alguma tem
  lançamentos, a carga falha (`money.import.account_type`).
- A data-limite vem da opção "Day Threshold for Read-Only Transactions": `hoje − N dias` (0 = nenhuma).
- Arquivo ilegível ou que não é XML válido falha (`money.import.unreadable`, `money.import.malformed`).
- A data da compra vem da nota (RN-48).

## 10. Persistência e concorrência

- **RN-70** — Um único lock: um caso de uso **altera** o livro por vez (as leituras não o usam; uma leitura durante a carga pode
ver o livro pela metade). Regra do domínio (`BusinessException`) e falha de
  storage (`BusinessError.Infrastructure`) viram `Failure` (`Rules.locked`, `Rules.stored`).
- **RN-71** — Repositórios sobre `Storage` (JSON), hoje `InMemoryStorage`: leitura do cache, escrita write-through;
  transações em arquivos `transactions-AAAA-MM.json` (um por mês da data; mudar a data move de arquivo). Registro
  ilegível faz a carga falhar (`money.storage.unreadable`); na partida o erro só é registrado no console. Contas são instâncias vivas referenciadas pelos splits:
  alteram-se no lugar e salvam-se, voltando ao estado anterior se a gravação falhar.
- **RN-72** — A tela mostra "Dados em memória": nada é gravado em disco no estado atual.

## 11. Catálogo de erros de domínio

| Chave `money.*` | Quando |
|-----------------|--------|
| `transaction.splits` | menos de 2 splits |
| `transaction.unbalanced` | Σ débito ≠ 0 |
| `transaction.not_found` | revisar/excluir id inexistente |
| `transaction.description` | descrição vazia |
| `amount.positive` | transferência com valor ≤ 0 |
| `amount.scale` | mais de 2 casas, num split ou no saldo inicial |
| `period.closed` | data antes da data-limite |
| `account.not_found` | conta inexistente |
| `account.name` | nome vazio |
| `account.top_level` | mexer no topo ou criar sem mãe |
| `account.family` | tipo fora da família da mãe |
| `account.parent` | mãe é a própria conta ou descendente |
| `account.type_fixed` | mudar tipo com lançamentos/subcontas |
| `account.children` | excluir conta com subcontas |
| `account.target` | excluir conta com lançamentos sem destino válido |
| `account.opening_equity` | saldo inicial sem PL para contrapartida |
| `import.account`, `import.unreadable`, `import.malformed`, `import.account_type` | carga do livro (RN-50, RN-51) |
| `storage.unreadable` | registro do storage que não volta a ser lido |

Textos em `messages_pt_BR.properties` e `messages_en_US.properties`.

## 12. Intenções sem código e divergências do INTENT

| Item | Situação |
|------|----------|
| 🕒 F5/F6 — importação de extrato bancário em PDF e staging com sugestão de contrapartida | sem código |
| 🕒 F7 — exportação GnuCash e round-trip; slots `br-money/*` (compra, k, N, P) | só há leitura (RN-51), sem exportação nem tela de importação |
| 🕒 F10 — dívida total reconhecida na compra; arredondamento de parcela com resíduo na 1ª; parcela k > 1 vista pela 1ª vez reconhece só o saldo restante; casamento fatura ↔ compra por (descrição, k, N, valor) | só a **detecção** de parcela (RN-49); `purchase()` (UUID da compra original) é gravado, mas nada o preenche |
| Persistência PostgreSQL (INTENT §2, §8) | hoje JSON em memória (RN-71) |
| Modo simples × avançado | não existe (UI.md §11) |
| Uso em celular, PWA, app nativo | fora de escopo: o sistema é 100% web, só desktop (INTENT §1, §4) |
| Fatura por fechamento ou saldo devedor, no lugar do mês civil (RN-46) | em aberto; o `BACKLOG.md` não registra mais o item |
| Fora de escopo (INTENT §4): multi-moeda, conciliação, OFX/CSV, multiusuário, offline; fase 2: orçamento e recorrentes | — |
