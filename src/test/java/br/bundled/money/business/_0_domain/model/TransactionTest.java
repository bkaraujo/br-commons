package br.bundled.money.business._0_domain.model;

import br.commons.business.BusinessException;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
class TransactionTest {

    private static Account account(AccountType type) {
        val account = new Account();
        account.type(type);
        return account;
    }

    private static TransactionSplit split(Account account, String amount) {
        return new TransactionSplit() {
            private final UUID id = UUID.randomUUID();
            @Override public UUID identity() { return id; }
            @Override public Account account() { return account; }
            @Override public BigDecimal amount() { return new BigDecimal(amount); }
            @Override public long createdAt() { return 0; }
            @Override public long updatedAt() { return 0; }
        };
    }

    private static Transaction transaction(TransactionSplit... splits) {
        return new Transaction() {
            private final UUID id = UUID.randomUUID();
            @Override public UUID identity() { return id; }
            @Override public LocalDate date() { return LocalDate.of(2026, 10, 19); }
            @Override public BigDecimal installmentAmount() { return BigDecimal.ZERO; }
            @Override public TransactionStatus status() { return TransactionStatus.CONFIRMED; }
            @Override public List<TransactionSplit> splits() { return List.of(splits); }
            @Override public long createdAt() { return 0; }
            @Override public long updatedAt() { return 0; }
        };
    }

    @Test
    void expensePaidByCreditCard_isBalanced() {
        val result = transaction(
                split(account(AccountType.EXPENSE), "100.00"),
                split(new CreditCard(), "100.00"));
        assertTrue(result.balanced());
        assertDoesNotThrow(result::validate);
    }

    @Test
    void transferBetweenBanks_isBalanced() {
        val result = transaction(
                split(account(AccountType.BANK), "-50.00"),
                split(account(AccountType.BANK), "50.00"));
        assertTrue(result.balanced());
    }

    @Test
    void invoicePayment_isBalanced() {
        val result = transaction(
                split(account(AccountType.BANK), "-300.00"),
                split(new CreditCard(), "-300.00"));
        assertTrue(result.balanced());
    }

    @Test
    void expenseAgainstBankWithSameSign_isUnbalanced() {
        val result = transaction(
                split(account(AccountType.EXPENSE), "100.00"),
                split(account(AccountType.BANK), "100.00"));
        assertFalse(result.balanced());
        assertThrows(BusinessException.class, result::validate);
    }

    @Test
    void singleSplit_isRejected() {
        val result = transaction(split(account(AccountType.EXPENSE), "0.00"));
        assertThrows(BusinessException.class, result::validate);
    }

    @Test
    void unbalanced_rendersFromMoneyBundle() {
        val result = transaction(
                split(account(AccountType.EXPENSE), "100.00"),
                split(account(AccountType.BANK), "100.00"));
        val error = assertThrows(BusinessException.class, result::validate).getError();
        assertEquals("Transação " + result.identity() + " não está balanceada: Σ (valor × débito) ≠ 0",
                error.render(Locale.of("pt", "BR")));
        assertEquals("Transaction " + result.identity() + " is unbalanced: Σ (amount × debit) ≠ 0",
                error.render(Locale.US));
    }

    @Test
    void singleSplit_rendersFromMoneyBundle() {
        val result = transaction(split(account(AccountType.EXPENSE), "0.00"));
        val error = assertThrows(BusinessException.class, result::validate).getError();
        assertEquals("Transação " + result.identity() + " precisa de ao menos 2 splits", error.render(Locale.of("pt", "BR")));
    }

    @Test
    void debitAmount_followsGnuCashConvention() {
        assertEquals(new BigDecimal("-100.00"), split(new CreditCard(), "100.00").debitAmount());
        assertEquals(new BigDecimal("100.00"), split(account(AccountType.EXPENSE), "100.00").debitAmount());
    }

    @Test
    void purchase_isNullByDefault() {
        assertNull(transaction().purchase());
    }
}
