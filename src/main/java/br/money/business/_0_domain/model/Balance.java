package br.money.business._0_domain.model;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.YearMonth;

@NullMarked
public record Balance (
        YearMonth period,
        BigDecimal amount
) implements Comparable<Balance> {

    @Override
    public int compareTo(Balance other) {
        return period.compareTo(other.period);
    }

}
