package br.bundled.money.business._1_application;

import br.bundled.money.business._0_domain.model.AccountType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An account as the user describes it. {@code parent}: required (the top level is fixed). {@code opening}: only when
 * creating, the balance in the nature of the account (money in a bank, debt on a card), against the opening equity.
 */
@NullMarked
public record AccountInput(String name, AccountType type, @Nullable UUID parent, @Nullable BigDecimal opening) {}
