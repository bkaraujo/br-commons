# br.bundled.money — Double-entry bookkeeping

Produto definido em [INTENT.md](INTENT.md). Este arquivo é o guia de
arquitetura: leia antes de criar/mover código no pacote.

## 1. Camadas

```
br.bundled.money
├── MoneyApp                  ponto de entrada; só anexa módulos (Module.of)
├── business
│   ├── _0_domain             modelo, regras, contratos (repository/service)
│   └── _1_application        casos de uso; orquestra domínio + repositórios
├── persistence               implementa os repository do domínio (JDBC)
└── web                       HTMX server-side; borda HTTP
```

Regra de dependência (prefixo numérico = ordem; camada só importa as de
número menor ou `br.commons`/JDK):

| Camada            | Pode depender de                         | Nunca de                         |
|-------------------|------------------------------------------|----------------------------------|
| `_0_domain`       | `br.commons` (`business`, `lang`), JDK   | `_1_application`, `persistence`, `web` |
| `_1_application`  | `_0_domain`, `br.commons`                | `persistence`, `web`             |
| `persistence`     | `_0_domain`, `br.commons.persistence.jdbc` | `_1_application`, `web`        |
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

## 4. Web (`web`)

Mesmo padrão do `br.bundled.tracker.htmx`:

```
web
├── WebModule          Lifecycle: WebServer + HtmxScript + StaticFiles + register(rotas)
├── ui                 o que toda tela compartilha: Layout, Screen, Theme, Ui
├── <feature>          uma pasta por tela/feature
│   ├── *Routes        WebResource: lê request, chama aplicação, escolhe resposta
│   └── *Views         package-private: só monta Node (Html5 + Htmx DSL)
└── static             app.css, theme.js (servidos em /static/money/)
```

- Features dependem só de `ui` (e da aplicação); `ui` não depende de feature.
- `*Views` não acessa repositório nem regra: recebe dados prontos.
- Mesma URL serve página inteira e fragmento: `Ui.partial(request)` decide
  (htmx não-boosted e não history-restore → fragmento).
- Menu com `hx-boost`; filtros (mês, conta) na URL com `hx-push-url`.
- Cabeçalho (menu) fixo; navegação boosted troca só `<main id="content">`:
  `Layout.page(request, ...)` responde título + conteúdo (`HX-Retarget`) +
  menu `hx-swap-oob`. Toda página inteira passa por `Layout.page`.
- Todo JS fica no `<head>` do `Layout` (carregado uma vez, nunca no body nem
  em fragmento). Scripts agem por delegação no `document` / eventos htmx
  (`htmx:load`), para valer em conteúdo trocado sem recarregar.
- Campos declarados uma vez (`FieldSpec`) → renderizados com `DIALECT` e
  validados no servidor; erro ao lado do campo (`Ui.errorOf`).
- Inválido com htmx: `HX-Retarget` no form; sem htmx: post/redirect/get.
- Tema claro/escuro só no navegador (`theme.js` + `light-dark()` no CSS).
- **Toda dropdown tem filtro ao vivo**: renderize `<select>` comum (com opção
  vazia "Escolha…" como prompt); `static/combo.js` (carregado no `Layout`) o
  transforma em combobox filtrável no navegador, inclusive em conteúdo trocado
  pelo htmx. Não crie dropdown fora de `<select>` nem filtre no servidor.
- Mobile-first: toda tela utilizável em tela estreita (INTENT §7).
- Textos de UI em pt-BR via `Ui.message(...)`.

### 4.1 Mockup

Enquanto `_1_application`/`persistence` não existem, a web usa dados em
memória em `web/mock` (seed fictício). Regras:

- `mock` é o único lugar com dados fictícios; features o tratam como se fosse
  a camada de aplicação, para a troca futura ser só de dependência.
- `mock` respeita as regras do domínio (sinal, balanceamento) usando
  `AccountType` real.
- Nada de `mock` vaza para `_0_domain`.
- Dados reais: `-Dbr.commons.money.seed.gnucash=<arquivo .gnucash>` faz o
  `Seed` ler o livro GnuCash (`GnuCashSeed`) na inicialização. Dado real
  nunca entra no repositório (nem em código, nem em recurso).

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
- [ ] Erro com chave i18n, não texto cru.
