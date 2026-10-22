# br.money — pendências

Itens levantados em revisões de código e deixados para depois. Ao fazer um item, remova-o daqui.

## Revisão de 2026-10-22: lista de contas e cartões do extrato
1. **Fatura pelo mês civil** — o valor do cartão na lista são as compras datadas no mês, não a fatura (fechamento) nem
   a dívida em aberto: só funciona porque, no livro atual, compra e pagamento têm a mesma data. Fatura não paga ou
   compras de meses anteriores somem da lista. Decidir entre fatura por fechamento e saldo devedor.
