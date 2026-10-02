package br.commons.i18n;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;

/** An amount of money, formatted as currency by {@link Formats}. The domain's money type implements it. */
@NullMarked
public interface CurrencyAmount {

    BigDecimal amount();
}
