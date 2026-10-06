package br.commons.locale.i18n;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount of money, formatted by {@link Formats} in its {@link #currency()}. The domain's money type implements it;
 * the currency is part of the value, not of the locale (R$ 10,00 shown in en-US is still reais: {@code R$10.00}).
 */
@NullMarked
public interface CurrencyAmount {

    Currency BRL = Currency.getInstance("BRL");

    BigDecimal amount();

    /** BRL unless the domain's type says otherwise. */
    default Currency currency() {
        return BRL;
    }
}
