# Roteiro visual: valores financeiros

Regra: todo valor em reais tem a mesma aparência em toda tela: `R$ 1.234,56` (ou `1.234,56` em campo),
algarismos tabulares, alinhado à direita, cor pelo sinal (`.neg` / `.pos`) onde for texto. A tela de referência é o **Extrato**.

Preparação: subir a app (run config do IDEA, porta 9000) com o seed carregado. Testar nos temas claro e escuro.

| # | Tela | Passos | Esperado |
|---|------|--------|----------|
| 1 | Extrato | Abrir `/statement`, olhar a coluna de valores | Referência: `R$ 1.234,56`, direita, tabular, negativo em vermelho |
| 2 | Detalhe | Clicar na descrição de uma linha | Campo **Valor** mostra `1.234,56` (vírgula, milhar com ponto), à direita, em negrito, sem setas de spinner |
| 3 | Detalhe dividido | Abrir uma transação com 3+ contas | Colunas **Entra/Sai** em `1.234,56`, à direita; totais no rodapé em `R$ 1.234,56` |
| 4 | Edição | Lápis de uma linha | Valor já preenchido como em 2; apagar e digitar `123456` vira `1.234,56` (dígitos entram pela direita) |
| 5 | Edição | Salvar com valor vazio / `0,00` | Erro no campo, valor digitado permanece |
| 6 | Edição | Salvar valor alterado | Extrato mostra o novo valor no mesmo formato da linha 1 |
| 7 | Nova transação | Botão Nova, digitar `1` | Campo mostra `0,01`; placeholder `0,00` quando vazio |
| 8 | Divisão | "Dividir" a partir do formulário simples | Valor segue em `1.234,56` nas duas linhas; digitar em Entra/Sai atualiza os totais `R$ …` e a diferença |
| 9 | Lançamento rápido | Abrir o formulário rápido | Valor idêntico ao do 7 (mesmo estilo) |
| 10 | Conta nova | Cadastro de conta financeira, campo saldo inicial | Alinhado à direita |
| 11 | Comparação | Abrir 1 e 2 lado a lado | Mesma fonte, tamanho, algarismos tabulares e alinhamento |

Falhou qualquer linha: ajustar o CSS/view e ver `ValueStyleTest`, que guarda as regras que dá para checar sem navegador.
