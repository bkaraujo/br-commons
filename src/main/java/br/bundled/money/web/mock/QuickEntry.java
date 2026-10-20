package br.bundled.money.web.mock;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The quick entry of the mobile: a value between a financial account and an income or expense category. */
@NullMarked
public record QuickEntry(LocalDate date, BigDecimal amount, int account, int category, String description) {}
