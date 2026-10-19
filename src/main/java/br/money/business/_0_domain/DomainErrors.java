package br.money.business._0_domain;

import br.commons.business.BusinessError;
import br.commons.locale.i18n.I18N;
import org.jspecify.annotations.NullMarked;

/**
 * Translation keys of br.money domain errors, resolved in {@code br/money/messages_<locale>.properties}.
 * Loading this class registers the bundle in {@link I18N}. Keys are compile-time constants (inlined, they do not load
 * the class), so errors are built through {@link #rule}, which guarantees the registration.
 */
@NullMarked
public final class DomainErrors {

    public static final String PREFIX = "money.";
    public static final String BUNDLE = "br/money/messages";

    /** Arg: transaction identity */
    public static final String TRANSACTION_SPLITS = PREFIX + "transaction.splits";

    /** Arg: transaction identity */
    public static final String TRANSACTION_UNBALANCED = PREFIX + "transaction.unbalanced";

    static { I18N.register(PREFIX, BUNDLE); }

    private DomainErrors() {}

    public static BusinessError rule(String key, Object... args) {
        return new BusinessError.BusinessRule(key, args);
    }

}
