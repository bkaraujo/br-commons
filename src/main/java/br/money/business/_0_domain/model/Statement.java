package br.money.business._0_domain.model;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.time.YearMonth;
import java.util.List;

/**
 * Transactions for a particular account in a YearMonth scope
 */
@NullMarked
public interface Statement extends Comparable<Statement> {

    Account account();
    YearMonth period();
    List<Transaction> transactions();

    @Override
    default int compareTo(Statement other) {
        val result = account().compareTo(other.account());
        if (result != 0) return result;

        return period().compareTo(other.period());
    }
}
