# Estilo Visual

Este documento contém o estilo visual e as regras de interface desejados para o produto. Cada regra tem um id
(`UI-NN`); as regras valem para toda tela, salvo quando dizem o contrário.

## 1. Princípios

## UI-01 — HTML no servidor com htmx

A interface é HTML renderizado no servidor, com htmx (`br.commons.lang.htmx` + `Html5`); nenhum framework de mercado
(nem no servidor, nem no navegador). As views só montam o HTML a partir de dados prontos: não acessam repositório nem
aplicam regra de negócio.

## UI-02 — Textos por chave, formatos pt-BR

Todo texto de interface sai de uma chave de bundle (`forms_pt_BR` / `forms_en_US.properties`, prefixo `forms.`),
nunca escrito no código; toda chave nova existe nos dois idiomas. Dados do usuário (nomes, descrições) entram como
dado, sem tradução. Os scripts leem seus textos de um atributo do `<html>` (`data-texts`), preenchido pelo servidor.
Idioma padrão: pt-BR.

## UI-03 — Só desktop

Somente web, em navegador de desktop. Sem requisito de uso em celular: o CSS não tem regras para tela estreita, não há
PWA, manifest nem service worker.

## UI-04 — Melhoria progressiva, com limites

Sem JavaScript, links de navegação e formulários principais (abertos pelo próprio endereço) funcionam como GET/POST
comum. Controles que só existem para htmx (botões com `hx-get`, caixas de seleção que filtram sem botão de envio) podem
depender dele, mas nunca aparecem como controle morto: o que só funciona com JS nasce oculto e é revelado pelo JS.

## 2. Estrutura da página

## UI-10 — Cabeçalho fixo

Um cabeçalho `header.top` em cartão, grudado no topo (`position: sticky`). Da esquerda para a direita: marca (logo
quadrado em `--brand` + nome em negrito), menu, controles contextuais da tela (opcional; vazio não ocupa
espaço) e a escolha de tema, sempre na ponta direita. A altura real do cabeçalho é medida por script e gravada em
`--top-height`, da qual depende tudo que cola abaixo dele (`scroll-padding-top` inclusive).

## UI-11 — Conteúdo, janelas e rodapé

O conteúdo de cada tela fica em `<main id="content">`. Contêineres de janela que valem em qualquer tela ficam fora
dele, direto no `body`. Um rodapé fixo, no fim da janela, com uma linha de status em texto discreto; o `body` reserva a
altura dele embaixo. O `<title>` é "Tela · Produto".

## UI-12 — Menu

Lista horizontal de links em pílula. A tela atual fica em destaque (`aria-current="page"`: fundo `--soft`, texto
`--accent`, negrito). A página inteira já vem com o link marcado; nas navegações o `<main>` informa a tela
(`data-screen`) e um script marca o link correspondente, então o menu nunca volta do servidor. Cada rótulo reserva a
largura da sua versão em negrito (pseudo-elemento oculto com `attr(data-label)`), para marcar a tela não mexer na barra.

## UI-13 — Nada pula

Elementos que surgem depois (revelados pelo JS, trocados pelo htmx) reservam o próprio lugar (`visibility: hidden`
em vez de `display: none`, altura fixa igual à dos vizinhos) para a barra e o layout não se mexerem. A barra de rolagem
tem espaço sempre reservado (`scrollbar-gutter: stable`).

## 3. Navegação

## UI-20 — Boost

O `body` é `hx-boost`: todo link e formulário carrega a próxima página por htmx e empurra a URL no histórico, sem
recarregar. Os scripts do `<head>` carregam uma vez.

## UI-21 — Uma URL, só o que muda

Cada parte da tela que muda sozinha é uma região: `id` estável, montada pelo mesmo método da página inteira e
alcançável por GET. A resposta leva só a região que mudou, decidida pelo servidor a partir dos cabeçalhos da
requisição (com `Vary` correspondente):

- requisição comum ou restauração de histórico: página inteira;
- navegação *boosted*: só o conteúdo, retargetado em `#content`, e o título; partes do cabeçalho que dependem da tela
  vão fora de banda (`hx-swap-oob`) só quando mudam; o menu nunca volta; a janela volta ao topo;
- htmx não-boosted (um filtro, um botão): só o fragmento do painel.

Escrita na própria tela responde o fragmento do alvo mais as regiões vizinhas que mudaram, fora de banda. Escrita
feita numa janela que abre sobre qualquer tela avisa com um evento (`HX-Trigger`) e o `#content` se busca de novo da
URL atual (`hx-get=""`), sem voltar ao topo; nunca `HX-Refresh`. Resposta *boosted* sai sempre `200` (htmx descarta
resposta de erro sem trocar); a página inteira mantém o status real (ex.: 422). O JS não conhece as telas: quem decide
o que volta é o servidor.

## UI-22 — Estado na URL

Filtros (item escolhido, período, caixas marcadas) vão na query string e entram no histórico (`hx-push-url`). Uma URL
sem parâmetros abre no padrão da tela; parâmetro ausente ou inválido cai no padrão, nunca em erro. Voltar no histórico
mostra os controles como estavam.

## UI-23 — Sem htmx

Escrita bem-sucedida sem htmx faz post/redirect/get para a tela de origem. Recusa sem htmx devolve a página inteira com
o motivo e status `422`. Com htmx (inclusive *boosted*) as respostas são sempre `200`.

## UI-24 — Ligações entre telas

Todo item exibido que tem tela própria é um link para ela, levando o contexto (filtros, período) que faça sentido.
Avisos de sucesso trazem o link para ver o resultado ("Ver em …").

## 4. Visual

## UI-30 — Paleta

Paleta *Neo-Wealth* (segurança e crescimento), cada cor como `light-dark(claro, escuro)` em variáveis do `:root`.
Contraste WCAG AA: texto ≥ 4.5:1 sobre fundo, cartão e `--soft`, nos dois temas; bordas de controle ≥ 3:1. Cores
semânticas (positivo, negativo) se distinguem do destaque, e o sentido também vem no texto (sinal, palavra), nunca só
na cor.

| Papel | Tom | Hex | Uso |
|---|---|---|---|
| Primária (marca) | Deep Ink | `#0D1B2A` | texto no claro, fundo no escuro, logo (`--brand`) |
| Destaque (crescimento) | Electric Mint | `#10B981` | botão primário, tela atual, positivo |
| Secundária (foco) | Slate Blue | `#3B82F6` | links e interativos secundários (`--secondary`) |
| Alerta / despesa | Soft Coral | `#EF4444` | saída, orçamento estourado, ação destrutiva |
| Superfície clara | Cloud Gray | `#F8FAFC` | fundo no claro, texto no escuro |
| Superfície escura | Obsidian | `#1E293B` | cartão no escuro |

No claro, Mint, Slate Blue e Coral puros não passam 4.5:1 sobre branco: usam-se tons mais escuros da mesma família
(`#047857`, `#2563EB`, `#DC2626`); no escuro, os tons da paleta (ou mais claros) valem como estão.

```css
:root {
    color-scheme: light dark;
    --bg: light-dark(#f8fafc, #0d1b2a);
    --card: light-dark(#ffffff, #1e293b);
    --text: light-dark(#0d1b2a, #f8fafc);
    --muted: light-dark(#475569, #94a3b8);
    --line: light-dark(#e2e8f0, #334155);        /* separadores: decorativos */
    --line-strong: light-dark(#64748b, #7c8ba1); /* borda de controle e de totais: ≥ 3:1 */
    --brand: light-dark(#0d1b2a, #10b981);
    --brand-text: light-dark(#f8fafc, #0d1b2a);
    --accent: light-dark(#047857, #10b981);
    --accent-hover: light-dark(#065f46, #34d399);
    --accent-text: light-dark(#ffffff, #0d1b2a);
    --secondary: light-dark(#2563eb, #3b82f6);
    --soft: light-dark(#ecfdf5, #1a3a3a);
    --pos: light-dark(#047857, #34d399);
    --neg: light-dark(#dc2626, #f87171);
    --shadow-color: light-dark(rgb(0 0 0 / 6%), rgb(0 0 0 / 40%));
    --shadow: 0 1px 2px var(--shadow-color);
    --radius: 12px;
}
```

## UI-31 — Tipografia e página

Fonte do sistema (`system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`), `15px/1.5`. Página centralizada, até
`120rem`, com `padding` de `1.25rem 1rem`. `h2` de `1.2rem`; `h3` como rótulo de seção: `.75rem`, maiúsculas,
espaçamento `.04em`, cor `--muted` (dentro de janelas, o `h3` é o título: `1rem`, cor `--text`, sem maiúsculas).
Legendas (`.caption`) em `.8rem`, `--muted`.

## UI-32 — Superfícies

Fundo `--bg`; blocos em cartão (`.card`): fundo `--card`, `border-radius: var(--radius)`, sombra `--shadow`, sem borda.
Cartão de destaque (um total, o item final de um resumo) usa fundo `--soft`. Estado vazio: texto `--muted` centralizado,
com respiro (`2rem`).

## UI-33 — Números

Valores numéricos com `font-variant-numeric: tabular-nums`, sem quebra de linha e alinhados à direita (células e
campos). Positivo em `--pos`, negativo em `--neg`; onde o sinal importa, mostra-se `+`.

## UI-34 — Resumo

Um resumo de painel é uma grade de cartões iguais (`dl`, 4 colunas): termo em `--muted`, valor em `1.1rem`; o cartão do
resultado final em `--soft`.

## UI-35 — Ícones

Ícones em SVG inline, decorativos (`aria-hidden`), alinhados ao texto (`vertical-align: -.125em`). Ícones de ação de
linha (editar, excluir) desenhados por máscara (`mask`) com `currentColor`, para herdar a cor do botão.

## 5. Controles

## UI-40 — Campos

Rótulo acima do campo: `.75rem`, negrito 600, maiúsculas, `--muted`. Campos com largura total, `padding .6rem .7rem`,
borda `1px solid var(--line-strong)`, raio `8px`, fundo `--bg`. Foco visível em todo controle e link: `outline: 3px
solid var(--accent)` com `outline-offset: 2px`. Erro do campo logo abaixo dele, `.8rem`, `--neg`.

## UI-41 — Botões

Primário: fundo `--accent`, texto `--accent-text`, negrito 600, raio `8px`, `padding .6rem 1.2rem`, hover em
`--accent-hover`. Secundário (`.link`): transparente, texto `--secondary`. Destrutivo (`.danger`): fundo `--neg` (ou texto
`--neg` quando `.link`). Botão de ícone (`.icon-button`): círculo de `2rem`, hover em `--soft`. Grupos de botões em
linha (`.buttons`), espaço `.5rem`. Controles de alternância (ex.: tema) não são ações: o hover só realça o ícone.

## UI-42 — Toda lista suspensa tem filtro ao vivo

Renderiza-se um `<select>` comum, com a opção vazia "Escolha…"; um script o transforma em caixa filtrável no navegador,
inclusive em conteúdo trocado pelo htmx. Filtra sem acento nem caixa, por todos os termos digitados (em qualquer
ordem); teclado ↑ ↓ Enter Esc; Tab e perder o foco escolhem a correspondência. Sem correspondência: "Nenhum
resultado"; valor fora da lista: "Escolha um item da lista." A lista abre em cartão flutuante (raio `8px`, sombra
`0 6px 18px`), até `16rem` de altura com rolagem; a opção ativa em `--soft`/`--accent`. O campo de texto herda os
`aria-*` do select. Não se cria lista fora de `<select>` nem se filtra no servidor.

## UI-43 — Campos de valor

Campo numérico alinhado à direita, com teclado numérico (`inputmode`). Valores monetários formatados ao digitar, sempre
com 2 casas, os dígitos entrando pela direita (`1` → `0,01`, `123456` → `1.234,56`), marcador "0,00". O servidor
aceita e devolve o mesmo formato.

## UI-44 — Requisição em andamento

Durante uma troca htmx, o elemento que fez o pedido fica com `opacity: .6` (transição `.2s`).

## 6. Janelas

## UI-50 — Janela modal

Formulários de edição, criação e confirmação abrem numa janela modal: um contêiner `.modal` que, ao receber um cartão,
cobre a tela (`position: fixed; inset: 0`), centraliza o cartão e aplica fundo de vidro fosco (fundo translúcido +
`backdrop-filter: blur(10px) saturate(140%)`). O cartão tem até `40rem` (mais largo só quando o formulário pede), borda
suave e sombra profunda (`0 18px 48px`). Entra com animação curta (fade `.15s` no fundo, subida `.18s` no cartão).
Enquanto aberta, a página não rola. Fecha com Cancelar, clique no fundo ou Esc.

## UI-51 — Formulário em janela

Grade de 2 colunas, espaço `.9rem`; título e campos longos ocupam a linha inteira (`.wide`). Campo com foco inicial no
que mais se preenche. Campo fixo (não editável naquele estado) aparece como texto `--muted` com o motivo.

## UI-52 — Confirmação de exclusão

Excluir sempre abre uma confirmação na janela, dizendo o que será afetado; o botão de confirmar é destrutivo
(`.danger`) e nomeia a ação ("Excluir", "Mover e excluir"). Quando a exclusão é impossível, a janela mostra só o
motivo, sem o botão.

## 7. Tabelas e listas

## UI-60 — Tabelas

Tabelas dentro de cartões; separadores em `--line`, totais com borda superior em `--line-strong`. Linha com informação
secundária (contrapartida, situação) em bloco abaixo do texto principal (`.sub`: `.8rem`, `--muted`), sem quebrar a
borda da linha. Coluna de ações estreita (`width: 1%`, sem quebra), com botões de ícone. Linhas que não podem ser
alteradas não mostram ações. Itens futuros/previstos marcados por classe e por palavra ("previsto").

## UI-61 — Árvores e linhas recolhíveis

Hierarquias aparecem indentadas. Grupos com filhos recolhem e abrem por um botão (ou clique na linha fora de links),
com `aria-expanded`; os abertos são lembrados no navegador (`localStorage`) e voltam ao trocar o conteúdo.

## UI-62 — Gráficos

Gráfico desenhado por script a partir dos `data-*` das linhas de uma tabela, que é o conteúdo de verdade: o desenho é
`aria-hidden` e, sem JS ou para leitor de tela, vale a tabela. Proporções simples usam `<meter>`.

## 8. Formulários e validação

## UI-70 — Campos declarados uma vez

Cada formulário declara seus campos uma vez: a view os renderiza com os tipos e restrições do HTML5 e o servidor os
confere de novo, mais o que um campo sozinho não diz. O erro aparece ao lado do campo.

| Violação | Mensagem |
|----------|----------|
| obrigatório | "Obrigatório." |
| longo demais | "Texto longo demais." |
| abaixo do mínimo | "Informe um valor maior que zero." |
| passo | "Use no máximo 2 casas decimais." |
| outra | "Valor inválido." |

## UI-71 — Formulário inválido

O próprio formulário volta no lugar (`HX-Retarget` + `outerHTML`), com os valores digitados e os erros; numa janela,
volta dentro da janela. Sem htmx, a página volta com o formulário e os erros.

## UI-72 — Avisos

Sucesso e recusa de regra: nota `role="status"` no topo do painel ("Item "X" criado." / "atualizado." /
"excluído."), com link para o que resolve a recusa quando existir. Erros de campo e recusa dentro de janela:
`role="alert"`. Item que sumiu entre abrir e enviar: aviso próprio ("O item não existe mais."), nunca erro genérico.

## UI-73 — Sequência rápida

Formulário de inclusão usado em série, depois de salvar, volta limpo mas mantendo os campos de contexto (data, item de
origem), para incluir o próximo sem redigitar.

## 9. Formatação

## UI-74 — Dinheiro

`R$ 1.234,56`; negativo com sinal; moeda compacta em eixos de gráfico.

## UI-75 — Datas e caminhos

`20/10` em listas, `20/10/2026` em textos, mês por extenso ("outubro de 2026") em títulos e seletores, `MMM/yy` em
gráficos. Caminhos hierárquicos com " › ".

## 10. Tema

## UI-80 — Tema

Automático (segue o sistema), claro e escuro. A escolha fica só no navegador (`localStorage`, `<html data-theme>`),
aplicada por script sem `defer` antes da primeira pintura (sem piscar). O CSS usa só `light-dark()`;
`:root[data-theme="light|dark"]` fixa o `color-scheme`. O servidor desenha três botões de ícone redondos (`2rem`) num
grupo em pílula, que nasce oculto ocupando o lugar e é revelado pelo JS; o botão ativo (`aria-pressed="true"`) fica em
cartão com sombra e cor `--accent`.

## 11. Acessibilidade

## UI-82 — Acessibilidade

Controles com `aria-label`; botões de abrir/fechar com `aria-expanded`; ícones decorativos `aria-hidden`; janelas com
`role="dialog"`; todo gráfico com tabela equivalente; selects mantêm o rótulo; foco sempre visível (UI-40).

## 12. Scripts e estilos

## UI-90 — Scripts

Scripts pequenos, um por comportamento, todos no `<head>` do layout (carregados uma vez, junto do htmx; nunca no body
nem em fragmento). Agem por delegação no `document` ou em `htmx:load`, para valer em conteúdo trocado sem recarregar.
Exceção: pequenos `hx-on` inline em fragmentos (marcar a linha atual) e expressões de gatilho do htmx (fechar a
janela). Arquivos estáticos lidos do disco em desenvolvimento, sem rebuild.

## UI-91 — Folhas de estilo

Uma folha principal que só importa as partes, na ordem da cascata; o servidor entrega tudo num único arquivo (uma
requisição). O que várias telas dividem (base, cabeçalho, controles, janela, tabelas) fica em `css/<parte>.css`; o
estilo de uma tela fica em `css/screens/<tela>.css`. Sem framework CSS: variáveis, grid e flex.
