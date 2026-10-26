# br.bundled.money — Double-entry bookkeeping

Produto definido em [INTENT.md](INTENT.md); regras de negócio em [BUSINESS.md](BUSINESS.md); navegação e interface em
[UI.md](UI.md); estrutura, fluxos e diagramas em [ARCHITECTURE.md](ARCHITECTURE.md). Este arquivo é o guia de
convenções: leia antes de criar/mover código no pacote.
Pendências em andamento (incompletas): [BACKLOG.md](BACKLOG.md).

## 1. Camadas

```
br.bundled.money
├── MoneyApp                  ponto de entrada; registra Storage e Clock no Context e anexa os módulos
├── business
│   ├── _0_domain             modelo, regras, contratos (repository/service)
│   └── _1_application        casos de uso; orquestra domínio + repositórios
├── persistence               implementa os repository do domínio sobre Storage (JSON)
└── web                       HTMX server-side; borda HTTP
```

Regra de dependência (prefixo numérico = ordem; camada só importa as de
número menor ou `br.commons`/JDK):

| Camada            | Pode depender de                         | Nunca de                         |
|-------------------|------------------------------------------|----------------------------------|
| `_0_domain`       | `br.commons` (`business`, `lang`), JDK   | `_1_application`, `persistence`, `web` |
| `_1_application`  | `_0_domain`, `br.commons`                | `persistence`, `web`             |
| `persistence`     | `_0_domain`, `br.commons.persistence`, `br.commons.lang` | `_1_application`, `web` |
| `web`             | `_1_application`, `_0_domain` (leitura de tipos), `br.commons.web`, `br.commons.lang.html/htmx` | `persistence` |

- Zero frameworks de mercado: tudo sai de `br.commons`.
- Domínio expõe contratos (`repository`, `service`) como interfaces;
  implementação fica na camada de fora (persistence/application).
- Ligação das camadas acontece em `MoneyApp`/módulos (`Lifecycle`), nunca por
  `new` de implementação dentro do domínio.

## 2. Domínio (`business/_0_domain`)

- Convenção de sinal, natureza (`AccountType.debit/credit`), balanceamento,
  parcelas e data-limite: INTENT §6 é a fonte. Não duplicar aqui.
- Financeira x contábil é derivada do `AccountType` (`AccountNature`); não
  criar campo próprio.
- Valores `BigDecimal`, escala 2, BRL; timestamps `long` em millis.
- Violação de regra: `BusinessException(BusinessError...)` com chave em
  `DomainErrors`; texto nos `messages*.properties` (pt_BR e en_US).
- Entidades `@NullMarked`; nulos explícitos com `@Nullable`.

## 3. Aplicação (`business/_1_application`)

- Um caso de uso por operação do usuário (lançar, importar, fechar período...).
- Retorna `Result<T, BusinessError>`; não renderiza texto (render só na borda).
- Não conhece HTTP, HTML nem SQL.
- `ApplicationModule` (Lifecycle) registra os serviços no `Context`; cada serviço
  pega os repositórios (implementados pela persistência) do `Context` no
  construtor; um lock único: um caso de uso altera o livro por vez.
  Regra do domínio (`BusinessException`) e falha de storage
  (`BusinessError.Infrastructure`) viram `Failure` (`Rules.locked`/`stored`).
- Saldos: `Balances` (implementa `BalanceService`) lê o cache do
  `BalanceRepository` (saldo de fim de mês das próprias splits, por mês com
  movimento); quem altera transações chama `refresh` a partir do mês alterado.
- Livro sem contas na inicialização começa do plano pessoal (F8).

## 3.1 Persistência (`persistence`)

- Repositórios sobre `br.commons.persistence.Storage` (JSON via
  `br.commons.lang.Json`, linhas = records): `InMemoryStorage` hoje
  (registrado em `MoneyApp`); trocar a implementação registrada não muda o resto,
  mas a troca por `LocalFileStorage` nunca foi exercitada. Layout no javadoc do pacote.
- `PersistenceModule.initialize` carrega tudo (contas antes das transações);
  leitura vem do cache, escrita é write-through. Registro ilegível para o start.
- Contas são instâncias vivas referenciadas pelas splits: alterar no lugar e
  `save`, nunca substituir por cópia.

## 4. Web (`web`)

Mesmo padrão do `br.bundled.tracker.htmx`:

```
web
├── WebModule          Lifecycle: WebServer + HtmxScript + StaticFiles + register(rotas)
├── ui                 o que toda tela compartilha: Layout, Screen, Theme, Ui
├── <feature>          uma pasta por tela/feature
│   ├── *Routes        WebResource: lê request, chama aplicação, escolhe resposta
│   └── *Views         package-private: só monta Node (Html5 + Htmx DSL)
└── static             app.css e os scripts (theme, combo, chart, header, month, tree, outline, splits, history), em /static/money/
```

- Features dependem só de `ui` (e da aplicação); `ui` não depende de feature.
- `*Views` não acessa repositório nem regra: recebe dados prontos.
- **Só o que muda volta**: cada região da tela tem `id` estável, é montada pelo mesmo
  método da página inteira e tem URL GET; a resposta htmx leva só a região que mudou
  (UI.md UI-21). Quem decide é o Java, pelos cabeçalhos; o JS não conhece as telas.
- Mesma URL serve página inteira e fragmento: `Ui.partial(request)` decide
  (`HtmxRequest.fragment()`: htmx não-boosted e não history-restore; e não o `#content`
  se recarregando, `Ui.reload`, que recebe o conteúdo como a navegação boosted).
- Menu com `hx-boost`; filtros (conta, caixas) na URL com `hx-push-url`; o mês é
  navegado no cabeçalho (`Ledger.monthNav`, `month.js`; UI.md §4).
- Cabeçalho (menu) fixo; navegação boosted troca só `<main id="content">`:
  `Layout.page(request, ...)` responde título + conteúdo (`HX-Retarget`) + espaço
  do mês em `hx-swap-oob` só quando muda (`Month-Key`). O menu não volta: a página inteira marca
  o link da tela (`aria-current`) e `header.js` o acompanha pelo `data-screen` do `<main>`. Toda página inteira passa por `Layout.page`.
- Troca só de mês (`Ui.monthOnly`): a tela pode responder só o painel que depende
  dele (`Layout.region`), como Plano de contas e Parcelamentos.
- Escrita na própria tela: o alvo + as vizinhas que mudaram em `hx-swap-oob` (o mês do
  cabeçalho também, `Layout.monthChanged`). Seletor de região que a rota usa é constante da view (`PANEL`). Escrita
  sobre qualquer tela (Lançar): `HX-Trigger: book-changed` (no `body`) e o `#content` se
  recarrega (`hx-get=""`); nunca `HX-Refresh`.
- Todo JS fica no `<head>` do `Layout` (carregado uma vez, nunca no body nem
  em fragmento). Scripts agem por delegação no `document` / eventos htmx
  (`htmx:load`), para valer em conteúdo trocado sem recarregar.
- Campos declarados uma vez (`FieldSpec`) → renderizados com `DIALECT` e
  validados no servidor; erro ao lado do campo (`Ui.errorOf`).
- Inválido: Extrato e Plano de contas devolvem o formulário no lugar (`HX-Retarget`;
  o Extrato até sem htmx), Lançar (janela `#entry-modal`) devolve o formulário no lugar e, salvo, recarrega o
  conteúdo da tela (`book-changed`); post/redirect/get só em sucesso
  (UI.md UI-23, UI-72).
- Tema claro/escuro só no navegador (`theme.js` + `light-dark()` no CSS).
- **Toda dropdown tem filtro ao vivo**: renderize `<select>` comum (com opção
  vazia "Escolha…" como prompt); `static/combo.js` (carregado no `Layout`) o
  transforma em combobox filtrável no navegador, inclusive em conteúdo trocado
  pelo htmx. Não crie dropdown fora de `<select>` nem filtre no servidor.
- 100% web, só para navegador de desktop: sem requisito de uso em celular (INTENT §1, §7); o CSS
  não tem regras para tela estreita.
- Textos de UI em pt-BR via `Ui.message(...)`.

### 4.1 Livro da web e seed

- `web/book/Book`: o que as telas leem e escrevem, sobre os serviços de
  `_1_application`, em tipos do domínio (`Account`, `Transaction`); só
  cálculo de tela (árvore, caminho, saldo do dia, razão com saldo corrente).
  Escritas devolvem o `Result` do serviço; a rota mostra `error.render()`.
- Ids nas URLs e forms são o `UUID` da entidade (`Ui.pathId`, `Ui.uuid`).
- `web/seed`: dados de desenvolvimento carregados por `ImportService` no
  `WebModule.initialize`. `-Dbr.commons.money.seed.gnucash=<arquivo .gnucash>`
  lê o livro GnuCash (`GnuCashSeed`); sem ela, livro sem lançamentos recebe o
  fictício. Único lugar com dado falso; dado real nunca entra no repositório
  (nem em código, nem em recurso).

## 5. Convenções gerais

- `package-info.java` com `@NullMarked` em todo pacote; javadoc de pacote
  descreve papel e dependências.
- `lombok.val` para locais; records para dados imutáveis.
- Chaves de configuração com prefixo `br.commons.`.
- Ao terminar edição: checar warnings do IntelliJ (MCP idea) nos arquivos tocados.

## 6. Checklist antes de codar

- [ ] Camada certa? Dependência respeita a tabela do §1?
- [ ] Regra contábil já está no INTENT §6? Se nova, registrar lá primeiro.
- [ ] Na web: `*Routes` + `*Views` na pasta da feature; nada de regra na view.
- [ ] Erro de regra do domínio com chave i18n (`DomainErrors`); texto de tela via `Ui.message`
      (as mensagens de campo, `Forms.violation`, e os avisos de rota são texto pt-BR no código).
