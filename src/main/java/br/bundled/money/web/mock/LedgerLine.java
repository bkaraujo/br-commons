package br.bundled.money.web.mock;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;

/** A transaction seen from one account: its amount there, the running balance and the other side. */
@NullMarked
public record LedgerLine(MockTransaction transaction, BigDecimal amount, BigDecimal balance, String counterpart) {}
