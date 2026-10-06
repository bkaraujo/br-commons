package br.commons.i18n;

import br.commons.locale.i18n.CurrencyAmount;
import br.commons.locale.i18n.Text;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Bundle used by the tests; mirrors how the application declares its bundles. */
public interface TestMessages {

    enum Color { RED, GREEN }

    Text greeting(String name, LocalDate day);

    Text importAlreadyImported(LocalDate confirmedOn);

    Text total(CurrencyAmount amount, YearMonth month, BigDecimal count);

    Text apostrophe();

    Text color(Color color);
}
