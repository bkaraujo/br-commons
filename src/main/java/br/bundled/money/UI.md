# br.money — Navegação e interface

> Extraídas do código em 2026-10-23. Regras de negócio (o que as telas calculam e recusam): [BUSINESS.md](BUSINESS.md).
> Produto e UX pretendidos: [INTENT.md](INTENT.md) §7. Arquitetura da camada web: [AGENTS.md](AGENTS.md) §4.
>
> Cada regra tem um id (`UI-xx`) e a **Fonte**. As marcadas 🕒 são intenção do INTENT ainda sem código; as demais
> estão implementadas. As referências `RN-xx` apontam para o BUSINESS.md.

## 1. Princípios

**UI-01** — HTML renderizado no servidor com htmx (`br.commons.lang.htmx` + `Html5`); nenhum framework de mercado.
Os `*Views` só montam o HTML a partir de dados prontos; as regras ficam no domínio, nos serviços e no `web.book.Book`.

**UI-02** — Textos de interface por chave no bundle `forms_pt_BR`/`forms_en_US.properties` (`src/main/resources/br/bundled/money`, prefixo `forms.`, registrado no `I18N` como o `messages`; idioma do processo por `L10N`, pt-BR por padrão), via `Ui.message(...)`; dados do usuário (nomes, descrições) entram com
`Text.data(...)`, sem tradução. Formatos de data e dinheiro seguem pt-BR.

**UI-03** — Somente web, em navegador de desktop: não há requisito nem desejo de uso em celular (INTENT §1, §7). O CSS
não tem regras para tela estreita: o layout é de desktop, e o cabeçalho com o menu fica sempre no topo.

**UI-04** — Melhoria progressiva, **com limites**: sem JavaScript os links (menu, relatórios, links de mês) e os
formulários de Lançar, de conta e de fechamento (abertos pelo endereço) funcionam como GET/POST comum. Dependem de htmx e não funcionam sem ele:
os botões do Extrato ("+ Novo lançamento", editar, excluir), os do Plano de contas ("+ Nova conta", editar, excluir,
fechamento),
"Dividir em várias contas" e "+ Linha", as caixas de seleção da Visão Geral, do Extrato e de Parcelamentos (os
formulários não têm botão de envio) e a lista de meses. O JS também traz o filtro de listas, o gráfico e os totais ao
digitar.

## 2. Estrutura da página

**UI-10 — Cabeçalho fixo (`header.top`).** Da esquerda para a direita: marca "br.money", menu (UI-12), navegação de mês
(UI-30; vazia nas telas sem mês) e a escolha de tema (UI-80). Fica grudado no topo; sua altura real vai para
`--top-height` (`header.js`), de que o CSS depende para o que cola abaixo dele.

**UI-11 — Conteúdo e rodapé.** O conteúdo de cada tela fica em `<main id="content">`, e o contêiner `#entry-modal` do
lançamento rápido (UI-51), vazio até ser aberto, fica fora dele, no corpo da página, para valer em qualquer tela; o rodapé diz "Dados em memória"
(RN-72). O `<title>` é "Tela · br.money".

**UI-12 — Menu.** Seis telas, nesta ordem; a atual fica em destaque (`aria-current`): a página inteira já vem marcada e,
nas navegações, o `<main id="content">` diz a tela (`data-screen`) e `header.js` marca o link com o mesmo `data-screen`,
então o menu não volta do servidor a cada navegação. Cada rótulo reserva a largura da versão em negrito, para marcar a tela não mexer na barra.

| Tela | Caminho | Rótulo |
|------|---------|--------|
| HOME | `/` | Visão Geral |
| STATEMENT | `/statement` | Extrato |
| INSTALLMENTS | `/installments` | Parcelamentos |
| ENTRY | `/entries/new` | Lançar (abre como janela, UI-51) |
| ACCOUNTS | `/accounts` | Plano de contas |
| REPORTS | `/reports` | Relatórios |

Fonte: `Screen`, `Layout`.

## 3. Navegação

**UI-20 — Boost.** O `body` é `hx-boost`: todo link e formulário carrega a próxima página por htmx e empurra a URL no
histórico, sem recarregar. Os scripts do `<head>` carregam uma vez.

**UI-21 — Uma URL, só o que muda.** Cada parte da tela que muda sozinha é uma região: um `id` estável, montada pelo
mesmo método da página inteira e alcançável por GET. A resposta leva só a região que mudou, decidida pelos cabeçalhos
da requisição (`Vary: HX-Request, HX-Boosted, HX-History-Restore-Request, HX-Trigger, HX-Current-URL, Month-Key`):
- requisição comum, ou restauração de histórico: **página inteira** (`Layout.page`);
- navegação *boosted*: só o **conteúdo** retargetado em `#content` e o título; a navegação de mês vai fora de banda
  (`hx-swap-oob`) só quando muda (UI-30); o menu nunca volta (UI-12); a janela volta ao topo;
- troca só de mês (`Ui.monthOnly`): só a parte da tela que depende do mês (UI-31);
- o conteúdo se recarregando depois de um lançamento rápido (UI-52): como a navegação *boosted*, sem voltar ao topo;
- htmx não-boosted (um filtro, um botão): só o **fragmento** do painel (`Ui.partial`).

Os `POST` de escrita decidem por `isHtmx()`, que também é verdadeiro para um envio *boosted*, e respondem só o
fragmento do próprio alvo (`#quick`, `#statement-panel`, `#accounts`), mais as regiões vizinhas que mudaram, fora de
banda (a lista de contas do Extrato, o painel do Plano de contas); o lançamento rápido, que abre sobre qualquer tela,
avisa com um evento e a tela se recarrega (UI-52). Toda página inteira passa por `Layout.page`. A resposta *boosted* de
`Layout.page` sai sempre `200` (htmx descarta resposta de erro sem trocar, e um 404 pareceria clique morto); a página
inteira mantém o status real (ex.: 422).

**UI-22 — Estado na URL.** Filtros vão na query string e entram no histórico (`hx-push-url`): `account`, `month`
(`2026-10`), e nas telas com caixas de seleção `filtered=1` mais `account-<id>=1` por conta marcada. Uma URL sem
parâmetros abre no padrão: o mês de abertura (RN-35) e a primeira conta financeira (ou a primeira de todas).
Parâmetro de mês ausente, inválido ou fora de 1900–2100 cai no padrão; na **Evolução** o padrão é janeiro do ano do mês
de abertura (RN-62).

**UI-23 — Sem htmx.** Só algumas escritas fazem post/redirect/get para a tela de origem: criar lançamento, criar
conta, salvar a conta (ou o livro vazio), Lançar e o fechamento. Alterar e excluir lançamento e excluir conta respondem
sempre o fragmento, sem redirecionar. Recusa do serviço sem htmx mostra a página inteira com o motivo e **status 422**
no Extrato e no Plano de contas (com o fechamento); em Lançar o erro volta como `200`. Com htmx (inclusive *boosted*) as
respostas são sempre `200`. Só o Plano de contas aceita `POST` no mesmo caminho do `PUT` (formulário sem htmx); o
Extrato não tem equivalente para alterar nem excluir.

**UI-24 — Ligações entre telas.** Cada conta ou categoria de um relatório leva ao seu extrato (`/statement?account=…`);
no resultado e na evolução a ligação leva o mês, no balanço não; o painel do plano de contas tem "Abrir no extrato"; depois de um lançamento rápido, a
nota tem "Ver em <conta>".

## 4. Navegação de mês (no cabeçalho)

**UI-30 — Um seletor, no cabeçalho.** `Ledger.monthNav` é o único: "←" (mês anterior), a lista de meses, "→" (próximo)
e "Hoje" (só aparece fora do mês atual). Todas as telas que têm mês o usam: **Extrato, Parcelamentos, Plano de contas
e os relatórios de mês** (balanço, resultado, evolução). Nenhuma desenha o seu. Telas sem mês (Visão Geral, Lançar,
lista de Relatórios) deixam o espaço vazio. Trocar de tela troca o espaço junto, fora de banda, só quando a
navegação de mês muda: o `#month-slot` guarda uma chave do que mostra (`data-key`), `month.js` a manda em todo pedido
htmx (cabeçalho `Month-Key`) e o servidor só o reenvia quando a chave é outra (UI-21).

**UI-31 — Vale a URL em que a página está.** Como o seletor é do cabeçalho, ele não conhece a tela: `month.js` leva cada
controle para a URL atual com só `month=` trocado, mantendo o resto (a conta do extrato, as contas marcadas). O clique é
tratado antes do htmx e faz `htmx.ajax` com o próprio link como origem, o que dá a mesma navegação *boosted* (UI-21):
a tela decide o que volta (`Ui.monthOnly`: mesmo caminho, só o `month` diferente). No Extrato e nos relatórios, o
conteúdo inteiro (a lista de contas do Extrato tem valores do mês); no Plano de contas e em Parcelamentos, só o painel
(`Layout.region`), sem a árvore nem as caixas, que não dependem do mês, e sem rolar a janela. A lista de meses usa o
link oculto `.month-go` para seguir o mesmo caminho. Sem JavaScript, os links levam só o mês e a lista de meses não faz
nada (reage apenas ao evento de mudança).

**UI-32 — Lista de meses.** Do mais antigo entre o mês da transação mais antiga (hoje, sem transações) e o mês
mostrado, até 12 meses depois do maior entre o mês atual e o mês mostrado; do mais recente para o mais antigo, com o
mês mostrado marcado ("outubro de 2025"); é um `<select>` com o filtro ao vivo (UI-71).

**UI-33 — Posição.** O seletor fica à direita do cabeçalho, colado na escolha de tema (UI-80). A escolha de tema nasce
oculta e ocupa o lugar mesmo assim (`visibility: hidden`), para o seletor não pular quando o JavaScript a revela.

Fonte: `Ledger`, `Layout`, `static/month.js`, `static/app.css`.

## 5. Telas

### 5.1 Visão Geral (`/`)

**UI-40** — À esquerda, as contas financeiras em dois cartões, "Contas" e "Cartões" (um vazio some), cada conta com
caixa de seleção; todas marcadas na primeira visita. Marcar ou desmarcar recarrega só o gráfico (`/` como fragmento) com
as caixas dos dois cartões e empurra a URL com elas (UI-22; o formulário não tem botão de envio: sem htmx as caixas não
fazem nada). Só contas
financeiras que as telas oferecem (`Book#moneyAccounts`, sem STOCK nem MUTUAL, RN-05): categorias e contas técnicas
ficam no plano de contas (INTENT §7, "visões separadas"). A mesma lista vale no Extrato, em Parcelamentos e em Lançar.

**UI-41** — "Receitas x Despesas": os 12 meses até o mês de abertura das contas marcadas (RN-34), com a legenda "Últimos
12 meses · N conta(s)" ou "Nenhuma conta selecionada." A tabela dos meses (mais recente primeiro; Mês, Receitas,
Despesas) é o conteúdo; o gráfico tem legenda Receitas/Despesas e eixo em moeda compacta; `chart.js` desenha as linhas a partir dos `data-*` de cada linha (o desenho é `aria-hidden`). Sem JS, ou
para leitor de tela, vale a tabela.

### 5.2 Extrato (`/statement?account=…&month=…`)

**UI-43 — Lista de contas (esquerda, sempre visível).** "Contas" com o saldo ao fim do dia mostrado (hoje, se o mês é o
atual; senão o último dia do mês) e "Cartões" com o valor do mês (RN-46). Subcontas aparecem indentadas (até 3 níveis). Escolher uma conta carrega só o painel, mantém
o mês, marca a escolhida sem refazer a lista (preserva a rolagem) e empurra a URL.

**UI-44 — Painel do mês.** Cabeçalho "Extrato · tipo" + caminho da conta, "+ Novo lançamento" e o resumo (saldo inicial,
aumentos, reduções, saldo final: RN-32). Tabela Data, Descrição (com a contraparte embaixo), Valor, ações; sem coluna de saldo
(o resumo já mostra o do mês). Mês vazio: "Nenhum lançamento previsto para <mês>."

**UI-45 — Linhas.** Data depois de hoje: classe `planned` e "previsto". Data antes da data-limite: "fechado" e **sem
botões** de editar/excluir (RN-40). Em conta de cartão, a data é a da compra, quando existe (RN-47). A descrição é um
link para o detalhe do lançamento (UI-60); o mesmo vale nas linhas do Plano de contas (UI-54) e dos Parcelamentos.

**UI-46 — Editor em janela (`#statement-editor`).** "+ Novo lançamento" e os botões Editar e Excluir de cada linha
(botões só com `hx-get`: sem htmx não abrem) abrem o formulário numa janela modal; fecha com Cancelar, clique no fundo ou Escape. Novo lançamento: data de hoje se o mês é o atual, senão o dia 1;
a conta do extrato como origem (se abaixo do topo). Transação simples abre o formulário simples (Data, Valor, Descrição,
De, Para: "De (sai de)", "Para (entra em)"); qualquer outra (RN-25) abre direto o de splits. O formulário simples tem
"Dividir em várias contas", que leva os valores para o de splits.

**UI-47 — Formulário de splits.** Data, Descrição e uma linha por split: conta, "Entra (R$)", "Sai (R$)". "+ Linha"
acrescenta e o botão de cada linha remove (sempre ficam ao menos 2); quem acrescenta e remove é o servidor
(`/statement/transactions/split`). O rodapé da tabela tem a linha "Total" (Σ Entra e Σ Sai) e abaixo um aviso `role="status"`: `splits.js` mostra os totais e "Balanceado." / "Diferença: R$ x" enquanto se digita;
o servidor faz a mesma conta e confere ao salvar (RN-24). Débito e crédito nunca aparecem: só "De/Para" e "Entra/Sai".

**UI-48 — Excluir.** Abre uma confirmação com data, valor e rótulo ("O lançamento some de todas as contas.").

**UI-49 — Depois de salvar/excluir.** A resposta é o painel (com a nota do que foi feito) e, fora de banda, a lista com
os novos saldos. Notas: "Lançamento "X" criado." / "atualizado." / "excluído."; "O lançamento não existe mais." (sumiu
entre a abertura e o envio); "Lançamento em período fechado: recue o fechamento para alterá-lo." (com o link "Alterar fechamento", UI-57a); "Lançamento com mais
de duas contas: use a divisão em várias contas." (editar como simples um que não é). Erros de validação do formulário
voltam só o formulário no lugar (UI-72).

### 5.3 Parcelamentos (`/installments`)

**UI-50** — Mesmo desenho do extrato. À esquerda, uma caixa por conta que não é cartão (todas marcadas na primeira
visita); mudar uma carrega só o painel, enviando as caixas marcadas (`filtered=1`) e o mês, e empurra a URL (o formulário não tem
botão de envio: sem htmx as caixas não fazem nada).
Painel: "N conta(s) e os cartões" (ou "Nenhuma conta selecionada"), resumo (Parcelas, Valor do mês, Última parcela,
Restante; RN-49a) e a tabela Data, Conta, Descrição (com contraparte), Parcela (`k/N`, "última" na final), Valor.
Vazio: "Marque uma conta para ver as parcelas." ou "Nenhuma parcela em <mês>.". Data depois de hoje: "previsto".

### 5.4 Lançar (janela sobre a tela atual; `/entries/new`)

**UI-51 — Lançamento rápido, numa janela.** "Lançar" no menu **não troca de tela**: abre o formulário numa janela
modal sobre a tela em que o usuário está (`hx-get` de `/entries/new` em `#entry-modal`; a URL não muda), **sem listar os
lançamentos anteriores**. Fecha com Cancelar, clique no fundo ou Esc. Sem htmx o link leva à página `/entries/new`, com o
mesmo formulário. Campos nesta ordem: Data e Valor (com foco, em negrito, mesma altura dos demais; formatado ao digitar, sempre com 2 casas,
os dígitos entrando pela direita: `1` → `0,01`, `123456` → `1.234,56`; `money.js`) lado a lado, Conta, Categoria, Descrição e
Anotação (opcional, várias linhas, até 500 caracteres; RN-36).
Título "Lançamento rápido"; o campo Valor tem o marcador "0,00" e Descrição "Ex.: mercado, salário…". Categoria lista só as folhas de Receita/Despesa, pelo caminho; Conta lista as financeiras, por "tipo · nome" (só o nome
quando já é o tipo). Data padrão: hoje (RN-23). Botões "Lançar" e "Cancelar".

**UI-52 — Depois de lançar.** Na janela (o formulário leva o marcador `modal=1`), salvar com sucesso **fecha a janela e
recarrega o conteúdo da tela por trás**, sem recarregar a página: a resposta avisa com o evento `book-changed`
(`HX-Trigger`) e o `<main id="content">` se busca de novo da URL em que o navegador está (`hx-get=""`), passando a
mostrar o lançamento e os novos saldos; o mês, a conta e as caixas da URL são mantidos, e a tela pode abrir em outro mês
se o lançamento passar a ser o mais recente (RN-35). Erro de campo ou
recusa do serviço devolve o formulário no lugar, na própria janela; a recusa aparece dentro do formulário
(`role="alert"`). Na página (sem htmx, ou endereço aberto direto), o `#quick` é trocado: a nota "Lançado: descrição ·
valor." com "Ver em <conta>" e o formulário **limpo mas com a mesma data e conta**, para lançar vários em sequência;
sem htmx, em caso de sucesso: redirect para a Visão Geral e a recusa volta a página com `200`.

### 5.5 Plano de contas (`/accounts?account=…&month=…`)

**UI-53 — Árvore.** Uma tabela com um bloco por conta de topo; os que têm subcontas nascem **recolhidos** (os sem subcontas não recolhem nem
têm botão); clicar na linha do grupo (fora do
link) abre e fecha; os abertos ficam no `localStorage` (`tree-open`) e voltam ao trocar a árvore. Colunas: Conta, Tipo
(selo, destacado nas financeiras), Saldo, ações. O topo não tem ações (RN-10).

**UI-54 — Painel do mês.** Clicar numa conta carrega, ao lado, o painel dela, somente leitura: legenda "Lançamentos · <tipo>", resumo (com
sinal e cor, como no extrato) e a tabela Data, Descrição, Valor (sem sinal; sem saldo nem ações; vazio: "Nenhum
lançamento em <mês>."), com "Abrir no extrato". Sem conta pedida: a primeira financeira, senão a primeira de todas.

**UI-55 — Formulário de conta (janela `#account-editor`).** Aberto por "+ Nova conta" ou pelo Editar da linha (só
`hx-get`). Tipo (BANK, CREDIT, CASH, ASSET, LIABILITY, EQUITY, INCOME, EXPENSE), Conta-mãe (só as da mesma família, nunca a própria nem
descendente: RN-11, RN-12), Nome e, **só criando uma conta financeira** (BANK, CASH, CREDIT), "Saldo inicial (R$)"
(RN-16). Mudar o tipo recarrega o formulário (`/accounts/form`) com as mães e os campos do novo tipo. Com lançamentos
ou subcontas o tipo aparece fixo: "<tipo> · fixo: a conta tem lançamentos ou subcontas" (RN-14).

**UI-56 — Excluir.** Com subcontas: só o aviso, sem botão de excluir. Com lançamentos em período fechado: aviso
pedindo recuar o fechamento, com o botão "Alterar fechamento" (UI-57a). Com lançamentos: "N lançamento(s) serão movidos para a conta escolhida" e o campo
"Mover lançamentos para" (obrigatório), botão "Mover e excluir". Sem lançamentos: "A conta não tem lançamentos." e "Excluir". Salvar e excluir trocam
a árvore inteira, com a nota do resultado ("Conta "X" criada." / "atualizada." / "excluída.", ou "excluída; N
lançamento(s) movidos para "Y"") ou o motivo da recusa, e, fora de banda, o painel ao lado: da conta e do mês da página
(a primeira financeira, se a conta não existe mais), com a linha dela marcada na árvore.

**UI-57 — Novo livro vazio.** "Novo livro vazio" (e, ao criar, a nota "Livro vazio criado com o plano de contas pessoal.") abre a confirmação com quantas contas e lançamentos em memória serão
descartados ("Nenhum arquivo é alterado.") e o tamanho do plano (RN-18); "Criar livro vazio" confirma.

**UI-57a — Fechamento de período (`/accounts/closing`).** Uma ação do livro, não um relatório, e **por mês**: fechar
um mês fecha tudo até o último dia dele (a data-limite do livro, RN-40, passa a ser o dia 1 do mês seguinte); o dia não
se escolhe. Na barra do Plano de contas, ao lado de "Novo livro vazio", o botão tem o cadeado à esquerda e, à direita, o
último mês fechado como `2025/10` (nada fechado: cadeado aberto e "Fechar período"); abre a janela no
`#account-editor`, como o formulário de conta. A janela tem a legenda "Nenhum período fechado." ou "Lançamentos até o fim
de outubro de 2025 estão fechados: não podem ser criados, editados nem excluídos."; o campo "Fechado até (mês)"
(`input type="month"`), "Salvar", "Remover fechamento" (só com algo fechado) e "Cancelar". Recuar o mês pede confirmação
na própria janela ("Recuar o mês de fechamento reabre os lançamentos do período para alteração.", "Confirmar
reabertura"); remover também ("Remover o fechamento reabre todos os lançamentos para alteração.", "Confirmar remoção"),
com o campo travado e "Cancelar". Mês inválido: "Mês inválido." (`role="alert"`, na janela). Salvar fecha a janela e troca a árvore
inteira, **sem nota** (o próprio botão mostra o mês); recusa do serviço aparece como nota, como nas outras escritas (UI-56). Sem htmx: o endereço abre a página com a janela, salvar faz
post/redirect/get para `/accounts`, e o mês inválido volta com 422. "Alterar fechamento" leva a ela de onde o período
fechado barra: a nota do Extrato (lançamento fechado, UI-49) e a exclusão de conta com lançamentos fechados (UI-56).
Uma data-limite que não cai no dia 1 (vinda do GnuCash) aparece como o mês do dia anterior a ela.

### 5.6 Relatórios (`/reports`)

**UI-58 — Lista.** Um cartão com uma lista de três links, cada um com nome e resumo (ex.: "Ativo, passivo e patrimônio líquido no fim do mês"): Balanço patrimonial, Demonstração do resultado e Evolução de gastos.
O fechamento de período não é relatório: fica no Plano de contas (UI-57a); `/reports/closing` redireciona para ele. Cada relatório de mês abre em `/reports/balance|income|evolution?month=…` com o link "←
Relatórios"; o mês é navegado no cabeçalho (UI-30). A troca de mês, sendo *boosted*, troca a tela inteira.

**UI-59 — Conteúdo.** Cálculos em RN-60 a RN-62. Balanço: cada grupo com **todos** os descendentes (indentados), cada um levando ao extrato (sem mês); só folhas e contas
financeiras mostram valor; "Resultado acumulado" no PL e os totais "Total do ativo" × "Passivo + PL + resultado". Resultado: barra (`<meter>`) com a fatia de cada conta no total do grupo;
"Déficit do mês" (se negativo) ou "Superávit do mês", e "Taxa de poupança: N%" (só com receita). Evolução: legenda "% da receita do mês; média dos meses até <mês>"; tabela rolável com 12 meses, Total (com a % do período) e Média;
valor zero é "—" sem link; cada mãe tem
botão que abre e fecha as subcontas (`outline.js`: nasce com o primeiro nível aberto, e as abertas passam para a tabela
do mês seguinte).

## 6. Formulários e validação

**UI-70 — Campos declarados uma vez.** Cada formulário declara seus campos (`FieldSpec`): a view os renderiza com os
tipos e restrições do HTML5 e o servidor os confere de novo, mais o que um campo sozinho não diz. O erro aparece ao lado
do campo.

| Violação | Mensagem |
|----------|----------|
| obrigatório | "Obrigatório." |
| longo demais | "Texto longo demais." |
| abaixo do mínimo | "Informe um valor maior que zero." |
| passo | "Use no máximo 2 casas decimais." |
| outra | "Valor inválido." |
| data fechada | "Período fechado: escolha uma data a partir da data-limite." |

Campos próprios: "Escolha uma categoria.", "Escolha uma conta.", "Escolha uma conta diferente da origem." (vale para
qualquer "Para" inválido, não só o igual à origem), "Já existe
uma conta com este nome aqui.", "Conta-mãe não aceita este tipo.", "Escolha a conta de destino.", "Informe ao menos
duas contas.", "O que entra precisa ser igual ao que sai.", "Informe Entra ou Sai (só um), maior que zero, com até 2
casas."

**UI-71 — Toda lista suspensa tem filtro ao vivo.** Renderiza-se um `<select>` comum (com a opção vazia "Escolha…") e
`combo.js` o transforma numa caixa filtrável: sem acento nem caixa, por todos os termos ("merc alim" acha "Despesas ›
Alimentação › Mercado"); teclado ↑ ↓ Enter Esc. Sem correspondência: "Nenhum resultado"; o marcador do campo é o rótulo da opção vazia; Tab e perder o foco escolhem a
correspondência; valor fora da lista: "Escolha um item da lista." Vale também para o conteúdo trocado pelo htmx. Não se cria lista fora de
`<select>` nem se filtra no servidor.

**UI-72 — Formulário inválido.** No Extrato e no Plano de contas o próprio formulário volta no lugar (`HX-Retarget` +
`outerHTML`) com os valores e os erros; o Extrato faz isso **mesmo sem htmx** (não confere o cabeçalho), o Plano de
contas sem htmx devolve a página com os erros. Em Lançar, na janela o próprio formulário volta no lugar; na página a
seção `#quick` inteira é trocada.

**UI-73 — Janelas e avisos.** Formulários de edição e confirmações são `role="dialog"` numa janela modal; avisos de
sucesso e as recusas de serviço do Extrato e do Plano de contas são `role="status"`; erros de campo e a recusa de Lançar,
`role="alert"`.

## 7. Formatação

**UI-74** — Dinheiro em `R$ 1.234,56`, negativo com sinal. Valores são coloridos: `neg` negativo, `pos` positivo (onde o
sinal importa mostra-se `+`).

**UI-75** — Datas: `20/10` nas listas, `20/10/2026` nos textos, mês por extenso ("outubro de 2026") e `MMM/yy` no
gráfico e na evolução (a coluna "Mês" da Visão Geral usa o mês por extenso). Caminhos de conta com " › ".

## 8. Tema

**UI-80 — Tema.** Automático (segue o sistema), claro e escuro; a escolha fica só no navegador (`localStorage`,
`<html data-theme>`), aplicada por `theme.js` antes da primeira pintura (sem piscar). O CSS usa `light-dark()`. O
servidor só desenha os três botões, num grupo que nasce oculto e é revelado pelo JavaScript (sem JS não há botão morto).

## 9. Acessibilidade

**UI-82 — Acessibilidade.** Controles com `aria-label`; botões de abrir/fechar com `aria-expanded`; ícones decorativos
`aria-hidden`; o gráfico tem a tabela como equivalente (UI-41); os selects mantêm rótulo e o campo de texto herda os
`aria-*` do select.

## 10. Scripts

Os scripts ficam no `<head>` do `Layout` (carregados uma vez, nunca no body nem em fragmento, junto do htmx) e agem por
delegação no `document` ou em `htmx:load`, para valer em conteúdo trocado sem recarregar. Exceção: pequenos `hx-on`
inline em fragmentos (marcar a linha ou o link atual) e expressões de gatilho do htmx (fechar a janela). Fica em `web/static/`, servido em
`/static/money/` (em desenvolvimento, lido do disco, sem rebuild).

| Script | Faz |
|--------|-----|
| `theme.js` | tema (UI-80); sem `defer`, para aplicar antes da pintura |
| `combo.js` | filtro ao vivo nos `<select>` (UI-71) |
| `chart.js` | gráfico de linhas da Visão Geral (UI-41) |
| `header.js` | altura do cabeçalho em `--top-height` (UI-10) e a tela atual no menu (UI-12) |
| `month.js` | navegação de mês do cabeçalho (UI-31) e a chave `Month-Key` de todo pedido htmx (UI-30) |
| `tree.js` | ramos recolhíveis do plano de contas, lembrados (UI-53) |
| `outline.js` | linhas recolhíveis da evolução (UI-59) |
| `splits.js` | totais do formulário de splits (UI-47) |
| `money.js` | valor do Lançar formatado ao digitar, sempre com 2 casas (UI-51) |
| `history.js` | voltar no histórico mostra as caixas como estavam (Visão Geral, Parcelamentos) |

**UI-60 — Detalhe do lançamento.** `GET /statement/transactions/{id}?account=…&month=…`, aberto pela descrição de
uma linha (UI-45): página da tela Extrato, somente leitura, com legenda "Lançamento", a descrição como título, "Ver no
extrato" (a conta e o mês de onde veio), Data, "Compra em" (quando há), Parcela (quando é parcelada), Valor, Anotação
("Sem anotação." quando não há; é o único lugar, além do Lançar, onde ela aparece) e a tabela Conta, Valor de cada
split. Lançamento que não existe mais: 404 com "O lançamento não existe mais." e o "Ver no extrato".

## 11. Intenções do INTENT §7 sem código

| Item | Situação |
|------|----------|
| 🕒 Modo simples × avançado (esconder PL, ajustes e splits) | não existe; o plano de contas e o formulário de splits mostram tudo |
| 🕒 Importação de PDF/GnuCash pela tela (desktop) | sem tela; o livro GnuCash só entra na partida (BUSINESS RN-51) |
| ✅ Visões separadas: "Minhas contas" (só financeiras) × árvore contábil completa | Visão Geral/Extrato/Parcelamentos × Plano de contas |
| Fora de escopo: uso em celular, PWA instalável, manifest, service worker | INTENT §1, §4: 100% web, só desktop; o CSS não tem regras para tela estreita |
| ✅ Lançamento rápido, saldos com drill-down no razão, relatórios | Lançar, Plano de contas, Relatórios |
