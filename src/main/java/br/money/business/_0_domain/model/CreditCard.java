package br.money.business._0_domain.model;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CreditCard extends Account {

    public CreditCard() { type(AccountType.CREDIT); }

}
