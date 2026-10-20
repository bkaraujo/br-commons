package br.bundled.money.web.mock;

import br.bundled.money.business._0_domain.model.AccountType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** The checked values of the account register; {@code opening} only on creation of a financial account. */
@NullMarked
public record AccountInput(String name, AccountType type, int parent, @Nullable BigDecimal opening) {}
