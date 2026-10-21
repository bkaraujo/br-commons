package br.bundled.money.business._0_domain.model;

import br.commons.annotation.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public class Account extends Entity implements Auditable {

    private UUID id = UUID.randomUUID();
    public final void identity(UUID id) { this.id = id; }
    @Override public UUID identity() { return id; }

    private AccountType type = AccountType.ASSET;
    public final AccountType type() { return type; }
    public final void type(AccountType type) { this.type = type; }

    /** {@code null} at the top level (Ativo, Passivo, Patrimônio líquido, Receitas, Despesas), which is fixed. */
    private @Nullable Account parent;
    public final @Nullable Account parent() { return parent; }
    public final void parent(@Nullable Account parent) { this.parent = parent; }

    /** Where the money is (bank, cash, card, investments), as opposed to a category or a technical account. */
    public final boolean financial() { return type.nature == AccountNature.FINANCE_ACCOUNT; }

    private long createdAt = System.currentTimeMillis();
    public final void createdAt(long desired) { this.createdAt = desired; }
    public @Override long createdAt() { return createdAt; }

    private long updatedAt = createdAt;
    public final void updatedAt(long desired) { this.updatedAt = desired; }
    public @Override long updatedAt() { return updatedAt; }
}
