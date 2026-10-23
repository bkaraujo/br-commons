# br.money — pendências

Este documento descreve o que o agente está trabalhando e está incompleto.

## Revisão da camada web htmx (2026-10-23)

Achados da revisão (`/code-review` max) das mudanças "só o que muda volta" (UI.md UI-21). Ainda não corrigidos.
Cada item: onde, o defeito, como reproduzir e a correção sugerida.

### Bugs

- [ ] **B1 — Tracker jQuery perdeu o destaque do menu** (`tracker/static/app.css:120`). A regra `.menu a.current`
  virou seletores `body:has(#content[data-screen=…])`, mas o tracker jQuery usa o mesmo CSS e marca com a classe
  `current` (`app.js:348`); seu `<main>` não tem `data-screen`. Reproduzir: rodar `tracker.jquery.TrackerApp`, abrir
  `/projects`: nenhum item marcado. Correção: manter também `.menu a.current` (ou `aria-current=page`).
- [ ] **B2 — Plano de contas: painel e cabeçalho em meses diferentes após salvar** (`AccountRoutes.saved`). Sem
  `month=` na URL, o mês do painel cai em `book.month()`, calculado depois da escrita; o `#month-slot` não vai junto.
  Reproduzir: livro com último lançamento em ago, hoje em out; criar conta com saldo inicial (ou "Novo livro vazio"):
  painel em outubro, cabeçalho em agosto. Correção: mandar também o `#month-slot` fora de banda em `saved()`.
- [ ] **B3 — Visão Geral: voltar no histórico mostra caixas que não batem com o gráfico** (`HomeViews.group`,
  `hx-push-url`). O snapshot do htmx guarda o atributo `checked` do servidor, não o estado atual. Reproduzir: A e B
  marcadas; desmarcar A, desmarcar B, voltar: as duas aparecem marcadas sobre o gráfico só de B. Correção: sincronizar
  `defaultChecked` em `htmx:beforeHistorySave`, ou re-renderizar as caixas junto com o gráfico.
- [ ] **B4 — Lançar: o recarregamento pode não acontecer** (`EntryRoutes.create`). O `HX-Trigger: book-changed` é
  disparado no form que fez o pedido; se a janela foi fechada antes da resposta (Esc, fundo, Cancelar), o form já saiu
  do DOM e o evento não chega ao `body`: grava, mas a tela fica com saldos velhos. Correção:
  `HX-Trigger: {"book-changed":{"target":"body"}}`.
- [ ] **B5 — `Ui.monthOnly` pega o clique no menu da própria tela** (`Ui.java:167`). Em `/accounts?month=…` (ou
  `/accounts` com aviso no editor), clicar "Plano de contas" devolve só o painel com `show:none`: árvore e aviso
  ficam, a janela não sobe. Idem `/installments`. Correção: exigir que o `month` tenha mudado de fato.
- [ ] **B6 — Árvore some ao salvar/excluir conta que já não existe** (`AccountRoutes.update`/`delete`, anterior ao
  diff). `ok("")` troca o `#accounts` (outerHTML) por nada. Reproduzir: excluir a conta numa aba e salvar a edição
  noutra. Correção: devolver a árvore com aviso "A conta não existe mais."
- [ ] **B7 — NPE em `Ui.monthOnly` com `HX-Current-URL` opaco** (`about:blank`, `mailto:x`): `getPath()` é `null`,
  só `IllegalArgumentException` é capturada → 500. Cabeçalho do cliente, gravidade baixa. Correção: tratar path nulo.

### Limpeza e convenções

- [ ] **L1 — Documentação desatualizada**: javadoc de `StatementViews.accounts` ("after a change or a new month"; o
  oob só vem de `changed()`); AGENTS.md §4 sobre `Ui.partial` sem citar o reload; tabela de `SwapSpec` no
  `htmx/AGENTS.md` sem `noShow`; javadoc de `RegisterViews.panel` cita uma "navigation" que o painel não tem.
- [ ] **L2 — Seletores digitados nas rotas**: `"#installments-panel"` (`InstallmentRoutes`) e `"#account-panel"`
  (`AccountRoutes`) pertencem às views; renomear o id quebra o mês em silêncio (`htmx:targetError`). Expor constante
  na view; o painel também é montado duas vezes (ramos partial e region).
- [ ] **L3 — CSS copia o enum `Screen`**: um seletor `:has()` por tela (6 no money, 3 no tracker). Tela nova compila
  mas não destaca. Alternativa: gerar a regra a partir de `Screen.values()` ou marcar `aria-current` em `htmx:load`.
- [ ] **L4 — Tracker duplica o money**: `Ui.boosted`, `INTO_CONTENT`, ramo boosted de `Layout.page` e o `Vary`
  copiados e já divergentes; nenhum `Vary` inclui `HX-History-Restore-Request`. Mover os predicados (navegação,
  fragmento) para `HtmxRequest`.
- [ ] **L5 — `monthSlot` renderiza a navegação de mês até 3 vezes** e `Ledger.monthNav` varre as transações
  (`firstMonth`) mesmo quando o slot é omitido. Calcular a chave a partir de mês, hoje e `firstMonth`; um único
  builder `monthSlot(nav, key, oob)`.
- [ ] **L6 — Código morto em `Ui.monthOnly`**: `|| reload(request)` nunca é verdadeiro (htmx só marca `<a>`/`<form>`
  como boosted). Simplificar para `if (!HtmxRequest.of(request).boosted()) return false;`.
- [ ] **L7 — Visão Geral com `hx-include` de três ids fixos** (`HomeViews`): um único `<form>` em volta dos dois
  cartões, com o `filtered` dentro, dispensa o include (formato do `InstallmentViews`).
- [ ] **L8 — Warnings nos arquivos tocados** (AGENTS.md raiz): `tracker/static/app.css:20` usa `light-dark()` com
  sombras, inválido (só cores) → nenhum cartão do tracker tem sombra; usar `--shadow-color: light-dark(...)` como o
  money. Restam também 13 avisos de nulidade de `Result.Success(@Nullable T)` em `AccountRoutes`, `EntryRoutes`,
  `StatementRoutes` e rotas do tracker.
