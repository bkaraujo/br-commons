package br.bundled.money.business._0_domain;

import br.commons.business.BusinessError;
import br.commons.locale.i18n.I18N;
import org.jspecify.annotations.NullMarked;

/**
 * Translation keys of br.bundled.money domain errors, resolved in {@code br/bundled/money/messages_<locale>.properties}.
 * Loading this class registers the bundle in {@link I18N}. Keys are compile-time constants (inlined, they do not load
 * the class), so errors are built through {@link #rule}, which guarantees the registration.
 */
@NullMarked
public final class DomainErrors {

    public static final String PREFIX = "money.";
    public static final String BUNDLE = "br/bundled/money/messages";

    /** Arg: transaction identity */
    public static final String TRANSACTION_SPLITS = PREFIX + "transaction.splits";

    /** Arg: transaction identity */
    public static final String TRANSACTION_UNBALANCED = PREFIX + "transaction.unbalanced";

    /** Args: what was changed (a date, an account id), the closing date (INTENT F9) */
    public static final String PERIOD_CLOSED = PREFIX + "period.closed";

    /** Arg: transaction identity */
    public static final String TRANSACTION_NOT_FOUND = PREFIX + "transaction.not_found";

    /** No arg: the description is required */
    public static final String TRANSACTION_DESCRIPTION = PREFIX + "transaction.description";

    /** Arg: the amount, zero or negative where a positive one is expected */
    public static final String AMOUNT_POSITIVE = PREFIX + "amount.positive";

    /** Arg: the amount with more than 2 decimals */
    public static final String AMOUNT_SCALE = PREFIX + "amount.scale";

    /** Arg: account identity */
    public static final String ACCOUNT_NOT_FOUND = PREFIX + "account.not_found";

    /** No arg: the name is required */
    public static final String ACCOUNT_NAME = PREFIX + "account.name";

    /** Arg: account name. The top level (Ativo, Passivo...) is fixed: not created, changed nor removed */
    public static final String ACCOUNT_TOP_LEVEL = PREFIX + "account.top_level";

    /** Args: account type, parent name. A subaccount is of its parent's family (INTENT §6) */
    public static final String ACCOUNT_FAMILY = PREFIX + "account.family";

    /** Args: account name, parent name. The parent cannot be the account itself or a descendant */
    public static final String ACCOUNT_PARENT = PREFIX + "account.parent";

    /** Arg: account name. The type changes only while the account has neither transactions nor subaccounts */
    public static final String ACCOUNT_TYPE_FIXED = PREFIX + "account.type_fixed";

    /** Arg: account name. An account with subaccounts is not removed */
    public static final String ACCOUNT_CHILDREN = PREFIX + "account.children";

    /** Arg: account name. Removing an account with transactions needs another account, below the top level */
    public static final String ACCOUNT_TARGET = PREFIX + "account.target";

    /** Args: field, file, cause. A record of the storage that cannot be read back */
    public static final String STORAGE_UNREADABLE = PREFIX + "storage.unreadable";

    static { I18N.register(PREFIX, BUNDLE); }

    private DomainErrors() {}

    public static BusinessError rule(String key, Object... args) {
        return new BusinessError.BusinessRule(key, args);
    }

    public static BusinessError notFound(String key, Object... args) {
        return new BusinessError.NotFound(key, args);
    }

    public static BusinessError validation(String key, Object... args) {
        return new BusinessError.Validation(key, args);
    }

}
