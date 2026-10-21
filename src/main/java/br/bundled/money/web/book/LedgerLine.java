package br.bundled.money.web.book;

import br.bundled.money.business._0_domain.model.Transaction;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;

/** A transaction seen from one account: its amount there, the running balance and the other side. */
@NullMarked
public record LedgerLine(Transaction transaction, BigDecimal amount, BigDecimal balance, String counterpart) {}
