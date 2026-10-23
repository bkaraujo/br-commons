# br.money — INTENT

> Documento vivo. Construído por entrevista; define o produto final antes do código.
> Status: **aprovado** — documento vivo, atualizado conforme o protótipo evolui.

## 1. Visão

Aplicação **100% web** de contabilidade pessoal por partidas dobradas (double-entry
bookkeeping), tendo o [GnuCash](../../../../../.agents/gnucash) como referência de
modelo de domínio e comportamento.

- Uso pessoal, single-user. Fase atual: POC.
- Execução local: Docker + navegador.
- Evolução: deploy na AWS, acessado pelo navegador do desktop.
- **Somente web, para uso no desktop**: não há desejo de utilização em celular (sem app nativo, sem PWA
  instalável, sem layout pensado para tela pequena).

## 2. Restrições técnicas

- Máximo reuso de `br.commons`; **zero frameworks de mercado**.
- UI server-side em HTMX via `br.commons.lang.htmx` (+ `Html5`).
- Persistência: PostgreSQL via `br.commons.persistence.jdbc`.
- Chaves de configuração com prefixo `br.commons.` (convenção do repo).
- Leitura de PDF: `pdfbox` (já gerenciado no `pom.xml`).
- XML (GnuCash): **novo parser em `br.commons.lang`** (`Xml`, no mesmo estilo de
  `Json`/`Yaml`), sem dependência externa.
- Estrutura de pacotes existente: `business/_0_domain`, `business/_1_application`,
  `persistence`, `web`.

## 3. Escopo do MVP

| # | Funcionalidade | Notas |
|---|----------------|-------|
| F1 | Plano de contas hierárquico | Tipos Ativo, Passivo, PL, Receita, Despesa; contas classificadas em **financeiras** e **contábeis** (ver §6) |
| F2 | Lançamentos (transações com N splits) | Balanceada: Σ (valor × `debit` do tipo) = 0 (ver §6) |
| F3 | Razão por conta (register) | Saldo corrente; extrato mensal (`Statement`) |
| F4 | Relatórios básicos | Balanço Patrimonial, DRE por período, saldo por conta |
| F5 | Importação de extrato bancário em **PDF** | Conta corrente e cartão de crédito; um parser por banco/layout |
| F6 | Staging de importação | Linhas extraídas → sugestão de contrapartida por regras (descrição→conta, aprendidas dos lançamentos); usuário confirma; detecção de duplicadas |
| F7 | Importação e exportação GnuCash | Formato XML (`.gnucash` gzip); ida e volta. Elementos fora do MVP (outras moedas, investimentos, agendadas, orçamentos) preservados opacos e reemitidos no export sem alteração |
| F8 | Template de plano de contas pessoal BR | Aplicado ao criar livro vazio (alternativa ao import GnuCash) |
| F9 | Fechamento de período | Data-limite (equivalente ao *read-only threshold* do GnuCash): tudo antes dela é imutável. Reabrir = recuar a data intencionalmente → corrigir → avançar de novo |
| F10 | Compras parceladas no cartão | Dívida total reconhecida na compra; parcelas da fatura baixam o passivo |

## 4. Fora de escopo (MVP)

- Multi-moeda, cotações, investimentos (modelo reserva campo de commodity; fixo BRL).
- Conciliação (reconcile n/c/y).
- Importação OFX/CSV.
- Multiusuário.
- Uso em celular: app nativo, PWA instalável e layout mobile. O sistema é 100% web, só para navegador de desktop.
- Suporte offline (online obrigatório).

### Fase 2 (intenção registrada)

- Orçamento (planejado x realizado por conta/período).
- Transações recorrentes/agendadas (Scheduled Transactions do GnuCash).

## 5. Glossário (mapeamento GnuCash)

| br.money | GnuCash | Definição |
|----------|---------|-----------|
| Livro | Book | Conjunto de contas e transações |
| Conta | Account | Nó da árvore do plano de contas |
| Transação | Transaction | Evento com data, descrição e ≥2 splits |
| Split | Split | Débito/crédito de uma transação em uma conta |
| Extrato mensal | — | `Statement`: transações de uma conta em um mês (`YearMonth`) |
| Extrato bancário | — | PDF do banco/cartão importado (F5/F6); não confundir com extrato mensal |
| Data de criação | date-entered | `Auditable.createdAt`: quando a transação foi criada no sistema (millis desde o epoch) |
| Data da transação | date-posted | `Transaction.date()`: quando a transação ocorre nas contas — compra no cartão = data da compra; pagamento de fatura = data do pagamento; base de saldos, relatórios e data-limite |
| Moeda | Commodity | Fixo BRL no MVP |
| Conta financeira | Account type BANK, CASH, CREDIT, STOCK, MUTUAL | Onde o dinheiro está: conta corrente, poupança, carteira, cartão de crédito, investimentos (STOCK/MUTUAL sem suporte de UI no MVP) |
| Conta contábil | Demais account types | Categorias (Receita/Despesa), PL, ajustes, ativos/passivos genéricos |

## 6. Regras contábeis

- **Convenção de sinal: ponto de vista do tipo da conta.** Valor do split positivo
  = aumenta o saldo da conta segundo sua natureza; negativo = diminui. Saldo = Σ
  valores dos splits, sem conversão.
- `AccountType.debit`/`credit`: +1/−1 conforme natureza (devedora: BANK, CASH, STOCK,
  MUTUAL, ASSET, RECEIVABLE, EXPENSE; credora: CREDIT, LIABILITY, PAYABLE, EQUITY,
  INCOME).
- Transação balanceada: Σ (valor × `type.debit`) = 0.
- GnuCash (débito positivo): `valor_gnucash = valor × type.debit` no export;
  inverso no import.
- Classificação financeira x contábil **derivada do tipo GnuCash**, sem campo próprio:
  BANK, CASH, CREDIT, STOCK, MUTUAL = financeira (STOCK/MUTUAL sem suporte de UI no
  MVP); demais = contábil. Round-trip automático.
- Valores em `BigDecimal`, 2 casas, BRL.
- Timestamps `long` (`Auditable.createdAt`/`updatedAt`) = **milissegundos** desde o
  epoch Unix. `Transaction` e `Account` são `Auditable`.
- Arredondamento de parcela: P = total ÷ N, 2 casas, `RoundingMode.HALF_UP`;
  resíduo (total − N×P) vai para a **1ª parcela** (ex.: 100,00 em 3× = 33,34 +
  33,33 + 33,33).
- Parcelas **não são necessariamente mensais**: o banco pode omitir uma parcela e
  jogá-la para uma fatura posterior (ou cobrar duas na mesma). Casamento linha de
  fatura ↔ compra original é por (descrição, k, N, valor), nunca por mês esperado;
  parcela ausente não é erro. Dívida restante = soma das parcelas ainda não casadas.
- Fechamento por data-limite: criar/editar/excluir transação com data anterior à
  data-limite é rejeitado (`BusinessException`). Reabrir é ação explícita (recuar a
  data); após correção, avançar de novo. Data-limite faz round-trip com o GnuCash.
- Cartão de crédito = conta de Passivo (tipo CREDIT). Compra parcelada em N vezes de valor P:
  despesa total (N×P) na data da compra contra o passivo do cartão; cada parcela da
  fatura não gera nova despesa — é casada com a compra original.
- Hierarquia de tipos: subconta é da mesma família da mãe (ativo: BANK, CASH, STOCK,
  MUTUAL, ASSET, RECEIVABLE; passivo: CREDIT, LIABILITY, PAYABLE; EQUITY; INCOME;
  EXPENSE). Contas de topo (Ativo, Passivo, PL, Receitas, Despesas) são fixas.
- Saldo agregado: conta contábil soma as subcontas, cada split convertido para a
  natureza da mãe (valor × `debit` do tipo do split × `debit` do tipo da mãe); conta
  financeira mostra só o próprio saldo.
- Excluir conta com lançamentos: usuário escolhe a conta de destino; os splits são
  movidos para ela (valor convertido de natureza, mantendo o balanceamento) e então a
  conta é removida. Conta com subcontas não é excluída. Tipo só muda sem lançamentos.
- Parcelada vista pela 1ª vez já na parcela k>1 (compra anterior ao sistema):
  reconhece apenas o saldo restante (parcelas k..N) contra conta de
  ajuste/abertura; despesa anterior fica fora.
- Vínculo parcela ↔ compra original persistido em **slots** (KVP) do GnuCash na
  transação/split, sob namespace próprio (`br-money/...`: id da compra, k, N, P).
  GnuCash ignora e preserva esses slots; export → GnuCash → reimport mantém o vínculo.
- Pagamento de fatura = transferência Ativo (conta corrente) → Passivo (cartão).

## 7. UX

- 100% web, em navegador de **desktop**; online obrigatório. Sem PWA, sem manifest, sem service worker e sem
  requisito de uso em celular nem de layout para tela pequena.
- HTMX server-side.
- Todas as funcionalidades do MVP estão na web de desktop.
- **Visões separadas**: "Minhas contas" (financeiras com saldo) separada da árvore
  contábil completa.
- **Modo simples x avançado**: simples esconde contas contábeis técnicas (PL,
  ajustes, abertura) e splits — usuário vê "conta" e "categoria", nunca
  débito/crédito; avançado mostra tudo, como o GnuCash.
- **Lançamento rápido**: valor, conta, categoria, descrição, data (padrão hoje). Abre como janela sobre a
  tela atual, sem listar os lançamentos anteriores.
  "Conta" lista só contas **financeiras**; "categoria" lista só contas
  **contábeis** de Receita/Despesa.
- **Consulta de saldos**: árvore de contas com saldo, drill-down no razão.
- **Relatórios**: Balanço e DRE.
- Importação de PDF/GnuCash pela web.
- Sem modo "Transferência" no lançamento rápido; transferências entre contas
  financeiras (ex.: pagamento de fatura) via lançamento completo.

## 8. Dados, segurança e deploy

- Local: `docker compose` (app + postgres).
- AWS: alvo a decidir. Requisitos já fixos: imagem Docker stateless; Postgres
  externo configurável; autenticação **fora da app** (VPN/Cognito/ALB) — app não
  implementa login.

## 9. Critérios de sucesso (POC)

1. **Round-trip GnuCash**: importar livro real, exportar, reabrir no GnuCash sem
   perda; saldos idênticos.
2. **1 mês real via PDF**: importar extratos de 1 mês (conta + cartão) e chegar ao
   saldo informado pelo banco.
3. **Relatórios batem**: Balanço e DRE iguais aos do GnuCash para o mesmo livro.

## 10. Questões abertas

- Bancos/layouts de PDF do MVP: decisão adiada. Arquitetura de parser plugável; cada banco entra com PDFs de amostra + teste.
- Alvo de deploy AWS (EC2+compose, ECS+RDS, ...).
- Estratégia de backup do Postgres (local e AWS).
- Livro/Book no modelo: entidade para guardar GUID da conta raiz (round-trip
  GnuCash) e data-limite (F9)?
