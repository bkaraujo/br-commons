# Judas 

Esta é uma aplicação para gestão financeira pessoal baseado na técnica de [double entry bookkeeping](https://en.wikipedia.org/wiki/Double-entry_bookkeeping)

O objetivo maior é auxiliar na execução de orçamento familiar atravéz de ferramentas de planejamento e acompanhamento que 
facilitem perceber tendências que irão ferir o orçamento. O sistema deve também facilitar e permitir auditorias financeiras
de forma que seja claro e simples o compartilhamento de relatórios com outras pessoas para obter conselhos e orientações sobre
administração financeira familiar.

Como lider da família preciso identificar a direção dos recursos financeiros, bem como em benefício de quem ocorrem.
Dentro da vida financeira familiar existem diversos eventos e compromissos que precisam ser rastreados e vinculados ao
orçamento. O próprio orçamento é uma visão anualizada dos objetivos da família, contendo obrigações com o governo federal,
governo local, projetos pessoais, e compromissos futuros como educação.

O projeto é construído iterativamente a partir dos testes de aceitação produzidos por agente humano. A codifcação do produto
é feita via LLM a partir dos testes de aceitação.

## Convenções deste documento

- Cada funcionalidade tem um id `FEAT-NN`; cada critério de aceitação, um id `FEAT-NN.k`. Um teste de aceitação cita o
  id que cobre (javadoc do teste), e o código cita a funcionalidade (`FEAT-05`) onde aplica a regra.
- Moeda única BRL; valores com 2 casas; datas no fuso `America/Sao_Paulo`; "hoje" vem do `Clock` da aplicação.
- Fora de escopo: multi-moeda, investimentos com cotação, conciliação bancária, OFX/CSV, multiusuário, uso em celular,
  suporte offline. O sistema é 100% web para navegador de desktop.

## Glossário

| Termo | Definição |
|---|---|
| Livro | Plano de contas + lançamentos + data-limite |
| Conta | Nó da árvore do plano de contas; tem tipo e, fora do topo, uma conta-mãe |
| Conta financeira | Onde o dinheiro está: BANK, CASH, CREDIT (cartão), STOCK, MUTUAL |
| Conta contábil | As demais: categorias (Receita/Despesa), Patrimônio líquido, ativos e passivos genéricos |
| Lançamento | Transação: data, descrição e ≥ 2 partidas (splits) que se anulam |
| Partida | Valor de um lançamento em uma conta, na natureza dela |
| Natureza | Débito (`debit` = +1) ou crédito (`debit` = −1), derivada do tipo da conta |
| Data-limite | Data antes da qual o livro é imutável |
| Beneficiário | Membro da família (ou terceiro) em benefício de quem um gasto ou receita ocorre |
| Compromisso | Marcação (tag) do lançamento que identifica o evento ou compromisso a que ele se vincula (imposto, matrícula, viagem) |

## FEAT-01 - Plano de Contas

Árvore hierárquica de contas, com tipos e naturezas no modelo do GnuCash.

| Tipo | Classe | Natureza |
|---|---|---|
| BANK, CASH, STOCK, MUTUAL | financeira | débito |
| CREDIT | financeira | crédito |
| ASSET, RECEIVABLE, EXPENSE | contábil | débito |
| LIABILITY, PAYABLE, EQUITY, INCOME | contábil | crédito |

- FEAT-01.1 A classificação financeira × contábil é derivada do tipo, sem campo próprio.
- FEAT-01.2 As contas de topo (Ativo, Passivo, Patrimônio líquido, Receitas, Despesas) são fixas: não são criadas,
  alteradas nem removidas; toda conta nova tem conta-mãe.
- FEAT-01.3 Uma subconta é da família da mãe: ativo (BANK, CASH, STOCK, MUTUAL, ASSET, RECEIVABLE), passivo (CREDIT,
  LIABILITY, PAYABLE), e cada um a sua: EQUITY, INCOME, EXPENSE.
- FEAT-01.4 A mãe nunca é a própria conta nem uma descendente dela.
- FEAT-01.5 Nome obrigatório, até 60 caracteres, único entre irmãs sem diferenciar caixa.
- FEAT-01.6 O tipo só muda enquanto a conta não tem lançamentos nem subcontas.
- FEAT-01.7 Saldo inicial, só na criação de BANK, CASH ou CREDIT: gera o lançamento "Saldo inicial", datado de hoje,
  contra a conta de Patrimônio líquido de abertura ("Saldos de abertura"; senão a única subconta do PL; senão o PL de
  topo). Se o lançamento não puder ser gravado, a conta criada é desfeita.
- FEAT-01.8 Conta com subcontas não é excluída. Sem lançamentos, é excluída direto. Com lançamentos, exige conta de
  destino (abaixo do topo, diferente da própria): cada partida é convertida à natureza do destino e somada à partida
  que o destino já tiver no lançamento; o lançamento que ficar com menos de 2 partidas é removido.
- FEAT-01.9 Livro novo começa do plano pessoal BR (Contas bancárias, Carteira, Investimentos, Cartões de crédito,
  Empréstimos e financiamentos, Saldos de abertura, categorias de Receitas e Despesas). Bancos e cartões o usuário cria
  depois, com saldo inicial.

## FEAT-02 - Razão por Conta

Extrato mensal de uma conta com saldo corrente.

- FEAT-02.1 Saldo no fim do dia D: conta financeira mostra só o próprio saldo; conta contábil soma a subárvore, cada
  partida convertida à natureza da conta pedida (`valor × debit(origem) × debit(destino)`).
- FEAT-02.2 Razão do mês: saldo inicial = saldo no dia anterior ao dia 1; linhas = lançamentos do mês que tocam a conta
  (ou a subárvore, se contábil), ordenados por data, criação e id; cada linha mostra contrapartida, valor e saldo
  corrente.
- FEAT-02.3 Resumo do mês: saldo inicial, aumentos, reduções e saldo final = inicial + aumentos + reduções.
- FEAT-02.4 As telas abrem no mês do último lançamento datado até hoje; sem lançamentos, no mês de hoje.
- FEAT-02.5 Lançamento com data futura aparece como previsto.

## FEAT-03 - Lançamentos Balanceados

Todo lançamento deve ter contrapartida contábil.

- FEAT-03.1 Convenção de sinal: o valor da partida é do ponto de vista da conta; positivo aumenta o saldo segundo a
  natureza dela. Saldo = Σ valores das partidas, sem conversão.
- FEAT-03.2 Balanceamento: Σ (valor × `debit` do tipo) = 0. Pelo menos 2 partidas depois de descartar as de valor zero.
- FEAT-03.3 Valores com no máximo 2 casas (zeros à direita não contam).
- FEAT-03.4 Descrição obrigatória (aparada), até 80 caracteres; anotação opcional até 500 caracteres.
- FEAT-03.5 Lançamento rápido: valor, conta (financeira), categoria (Receita ou Despesa sem subcontas), descrição, data
  (padrão hoje). Despesa: a conta paga a categoria; receita: a categoria paga a conta.
- FEAT-03.6 Transferência simples: valor > 0 sai da origem e entra no destino, origem ≠ destino. Pagamento de fatura é
  uma transferência da conta corrente para o cartão.
- FEAT-03.7 Lançamento completo: N linhas "conta + Entra | Sai", exatamente um dos dois por linha, Σ Entra = Σ Sai.
- FEAT-03.8 Revisão mantém a identidade do lançamento e das partidas das contas que permanecem; atualiza `updatedAt`.
- FEAT-03.9 Efeito no patrimônio de um lançamento = Σ das partidas em contas financeiras (receita positiva, despesa
  negativa, transferência zero).

## FEAT-04 - Lançamentos Parcelados

Compras parceladas são identificadas por um código único.

- FEAT-04.1 Compra parcelada em N vezes reconhece a dívida total na data da compra: despesa total contra o passivo do
  cartão; as parcelas da fatura baixam o passivo, sem nova despesa.
- FEAT-04.2 Cada parcela guarda o código da compra, o número k e o total N.
- FEAT-04.3 Valor da parcela P = total ÷ N, 2 casas, HALF_UP; o resíduo vai para a 1ª parcela
  (100,00 em 3× = 33,34 + 33,33 + 33,33).
- FEAT-04.4 Parcelas não são necessariamente mensais: o casamento linha de fatura ↔ compra é por (descrição, k, N,
  valor), nunca pelo mês esperado; parcela ausente não é erro. Dívida restante = Σ parcelas não casadas.
- FEAT-04.5 Compra vista pela primeira vez já na parcela k > 1 reconhece só o saldo restante (parcelas k..N) contra a
  conta de abertura.
- FEAT-04.6 Sem código, a parcela é reconhecida pela descrição: `k/N` (a última ocorrência válida) ou `PARC… k DE N`
  (prioritário); data completa (`05/10/2026`) não é parcela; exige N ≥ 2 e 1 ≤ k ≤ N.
- FEAT-04.7 Tela de parcelamentos do mês: quantidade, valor do mês, quantas são a última e o restante a pagar.

## FEAT-05 - Fechamento de período

Todas as informações de lançamento são imutáveis antes da data-limite.

- FEAT-05.1 Criar, revisar ou excluir lançamento com data **anterior** à data-limite é rejeitado; a própria data-limite
  é aberta. Ao revisar ou excluir, vale para a data nova e a antiga.
- FEAT-05.2 Vale também para o saldo inicial de conta nova e para lançamentos movidos ao excluir uma conta.
- FEAT-05.3 Fechar um mês põe a data-limite no dia 1 do mês seguinte. Avançar não pede confirmação.
- FEAT-05.4 Recuar ou limpar a data-limite é reabertura explícita e exige confirmação.
- FEAT-05.5 Toda reabertura e todo fechamento ficam registrados na trilha de auditoria (FEAT-14).

## FEAT-06 - Cartão de Crédito

- FEAT-06.1 Cartão é conta CREDIT: financeira, de natureza crédito. Compra aumenta a dívida; pagamento da fatura é
  transferência de conta financeira para o cartão.
- FEAT-06.2 Lançamento de cartão pode ter **data da compra** distinta da data do lançamento (ex.: vencimento da fatura);
  ela só informa: saldos, mês e ordem seguem a data do lançamento. O extrato do cartão mostra a data da compra quando
  existe.
- FEAT-06.3 Valor do cartão no mês = Σ das partidas no cartão dos lançamentos do mês, compras menos estornos,
  excluídas as transferências entre contas financeiras.

## FEAT-07 - Relatórios

- FEAT-07.1 Balanço patrimonial no último dia do mês: Ativo, Passivo, PL e Resultado acumulado (Receitas − Despesas);
  confere Ativo = Passivo + PL + Resultado acumulado; descendentes indentados e convertidos à natureza do grupo.
- FEAT-07.2 DRE do mês: receitas, despesas, fatia de cada conta de 2º nível no total do grupo; resultado "Superávit"
  (≥ 0) ou "Déficit"; taxa de poupança = resultado × 100 ÷ receitas (inteiro, HALF_UP), só com receitas > 0.
- FEAT-07.3 Evolução de 12 meses de todas as contas de Receitas e Despesas: valor e % da receita do mês; total do
  período; média dos meses decorridos até hoje. Valor não zero leva ao razão da conta no mês.
- FEAT-07.4 Gráfico do início: Receitas × Despesas das contas financeiras escolhidas; transferência entre elas não soma.

## FEAT-08 - Importação GnuCash

- FEAT-08.1 Importar substitui o livro inteiro (plano + lançamentos + data-limite). Tudo é montado e conferido antes de
  remover o atual; em falha, o livro anterior permanece.
- FEAT-08.2 Leitura do XML (gzip ou não): ignora modelos de agendamento e partidas zeradas; arredonda cada lançamento
  ao centavo e joga a sobra na partida de maior valor absoluto; converte tipos legados (CHECKING, SAVINGS, MONEYMRKT →
  BANK; CREDITLINE → CREDIT); conta de tipo sem equivalente com lançamentos faz a carga falhar.
- FEAT-08.3 A data-limite vem da opção "Day Threshold for Read-Only Transactions" (hoje − N dias).
- FEAT-08.4 Importação não aplica data-limite nem topo fixo; exige só lançamentos balanceados.
- FEAT-08.5 Exportar o livro para GnuCash e reabri-lo lá sem perda: saldos idênticos.

## FEAT-09 - Orçamento Anual

Visão anualizada dos objetivos da família, feita sobre o plano de contas (FEAT-01): não há estrutura própria de
linhas de orçamento; cada linha é uma conta.

- FEAT-09.1 Um orçamento por ano civil: valor planejado por mês para contas de Receita e Despesa do plano de contas.
- FEAT-09.2 O planejado é informado nas contas sem subcontas; a conta-mãe mostra a soma da subárvore, como o saldo
  (FEAT-02.1).
- FEAT-09.3 Os objetivos (obrigações com o governo federal, obrigações com o governo local, projetos pessoais,
  compromissos futuros como educação) são contas ou subárvores do plano de contas; agrupar por objetivo é ler o
  orçamento num ramo da árvore.
- FEAT-09.4 Planejado × realizado por conta e por mês; realizado = fluxo da conta no mês (FEAT-02).
- FEAT-09.5 Conta criada no plano de contas entra no orçamento com planejado zero; conta com planejado só é excluída
  com conta de destino, que recebe o planejado (como os lançamentos, FEAT-01.8).
- FEAT-09.6 Um orçamento pode ser copiado do ano anterior (planejado ou realizado) como ponto de partida.
- FEAT-09.7 Meses anteriores à data-limite (FEAT-05) têm o planejado imutável.

## FEAT-10 - Compromissos e Eventos

Eventos e compromissos rastreados. Compromisso é só uma marcação (tag) que identifica a que evento ou compromisso um
lançamento se vincula (ex.: "Viagem julho", "Matrícula 2027", "IPTU"). Não tem valor, data nem saldo próprios e não
altera saldos, orçamento nem relatórios contábeis.

- FEAT-10.1 Compromisso tem só um nome, único no livro sem diferenciar caixa; nome obrigatório.
- FEAT-10.2 Um lançamento pode ter zero, um ou vários compromissos; marcar e desmarcar é revisão do lançamento e
  respeita a data-limite (FEAT-05).
- FEAT-10.3 Consulta por compromisso: os lançamentos marcados, em qualquer período, com o total por conta.
- FEAT-10.4 Renomear um compromisso vale para todos os lançamentos marcados; compromisso marcado em algum lançamento
  não é excluído.

## FEAT-11 - Beneficiário

Identificar em benefício de quem os recursos se movem.

- FEAT-11.1 O livro tem uma lista de beneficiários (membros da família, a família como um todo, terceiros).
- FEAT-11.2 Cada partida em conta de Receita ou Despesa pode ter um beneficiário; sem beneficiário = a família.
- FEAT-11.3 Relatório por beneficiário: receitas e despesas no período, por conta.
- FEAT-11.4 O orçamento (FEAT-09) pode ser filtrado por beneficiário.

## FEAT-12 - Tendências e Alertas

Perceber tendências que irão ferir o orçamento.

- FEAT-12.1 Projeção do ano por conta: realizado até hoje + planejado dos meses restantes.
- FEAT-12.2 Alerta quando a projeção de uma conta (ou subárvore) passar do planejado anual.
- FEAT-12.3 Alerta quando o realizado de um mês passar do planejado do mês em percentual configurável.
- FEAT-12.4 Alerta quando o saldo projetado de uma conta financeira ficar negativo numa data futura.

## FEAT-13 - Compartilhamento de Relatórios

Compartilhar relatórios para obter conselhos.

- FEAT-13.1 Qualquer relatório (FEAT-07, FEAT-09, FEAT-11, FEAT-12) pode ser exportado em PDF com período, data de
  geração e data-limite vigente.
- FEAT-13.2 Exportação pode omitir descrições e anotações, mostrando só contas e valores.
- FEAT-13.3 Relatório exportado é só leitura: não dá acesso ao livro.

## FEAT-14 - Auditoria

Permitir auditorias financeiras.

- FEAT-14.1 Conta, lançamento e partida têm identificador UUID e `createdAt`/`updatedAt` em milissegundos.
- FEAT-14.2 Toda criação, revisão e exclusão de lançamento ou conta, toda importação e toda mudança de data-limite gera
  um registro de auditoria imutável: quando, o quê, valores antes e depois.
- FEAT-14.3 Trilha de auditoria consultável por período e por conta, e exportável junto do relatório (FEAT-13).

## FEAT-NN
