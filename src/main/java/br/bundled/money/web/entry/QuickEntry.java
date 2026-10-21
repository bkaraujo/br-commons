package br.bundled.money.web.entry;

import br.bundled.money.business._0_domain.model.Account;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The quick entry of the mobile: a value between a financial account and an income or expense category. */
@NullMarked
record QuickEntry(LocalDate date, BigDecimal amount, Account account, Account category, String description) {}
