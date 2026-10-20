package br.bundled.money.web.mock;

import br.bundled.money.business._0_domain.model.AccountNature;
import br.bundled.money.business._0_domain.model.AccountType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A node of the chart of accounts; {@code parent} is {@code null} for the top level (Ativo, Passivo...). */
@NullMarked
public record MockAccount(int id, String name, AccountType type, @Nullable Integer parent) {

    /** Where the money is (bank, cash, credit card), as opposed to a category or a technical account. */
    public boolean financial() {
        return type.nature == AccountNature.FINANCE_ACCOUNT;
    }

    /** A category of the quick entry: income or expense. */
    public boolean category() {
        return type == AccountType.INCOME || type == AccountType.EXPENSE;
    }
}
