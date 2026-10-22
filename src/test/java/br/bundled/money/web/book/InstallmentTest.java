package br.bundled.money.web.book;

import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.business._0_domain.model.BookTransaction;
import br.bundled.money.business._0_domain.model.CreditCard;
import br.bundled.money.business._0_domain.model.TransactionStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallmentTest {

    private static final BigDecimal AMOUNT = new BigDecimal("-33.33");
    private static final Account CARD = new CreditCard();

    private static LedgerLine line(String description, BookTransaction.Details details) {
        val transaction = new BookTransaction(UUID.randomUUID(), LocalDate.of(2026, 10, 5), description, List.of(),
                details, 0, 0);
        return new LedgerLine(transaction, AMOUNT, AMOUNT, "Mercado");
    }

    private static Optional<String> of(String description) {
        return Installment.of(line(description, BookTransaction.Details.single(AMOUNT.negate())), CARD)
                .map(installment -> installment.number() + "/" + installment.total());
    }

    @Test
    void readsTheFractionOfTheBill() {
        assertEquals(Optional.of("3/10"), of("LOJA X 03/10"));
        assertEquals(Optional.of("3/10"), of("LOJA X PARC 3 / 10"));
        assertEquals(Optional.of("3/10"), of("Loja X - Parcela 3 de 10"));
        assertEquals(Optional.of("10/10"), of("LOJA X 10/10"));
        assertEquals(Optional.of("52/200"), of("Hipoteca (52/200)"));
    }

    /** The last valid fraction counts: a store named with digits does not hide the installment. */
    @Test
    void theLastFractionCounts() {
        assertEquals(Optional.of("2/6"), of("LOJA 24/7 02/06"));
    }

    @Test
    void notAnInstallment() {
        assertTrue(of("Mercado").isEmpty());
        assertTrue(of("Pagamento 05/10/2026").isEmpty());
        assertTrue(of("LOJA 11/10").isEmpty());
        assertTrue(of("LOJA 00/10").isEmpty());
        assertTrue(of("LOJA 1/1").isEmpty());
        assertTrue(of("LOJA 1234/5678").isEmpty());
    }

    /** The installment of the transaction wins over the description. */
    @Test
    void theTransactionsInstallmentWins() {
        val details = new BookTransaction.Details(TransactionStatus.CONFIRMED, 4, 12, AMOUNT.negate(), UUID.randomUUID());
        val installment = Installment.of(line("LOJA X 03/10", details), CARD).orElseThrow();
        assertEquals(4, installment.number());
        assertEquals(12, installment.total());
    }

    @Test
    void remainingIsTheInstallmentsLeft() {
        val installment = Installment.of(line("LOJA X 03/10", BookTransaction.Details.single(AMOUNT.negate())), CARD)
                .orElseThrow();
        assertEquals(7, installment.left());
        assertEquals(new BigDecimal("-233.31"), installment.remaining());
        assertEquals(CARD, installment.account());
    }
}
