package br.bundled.money.web.account;

import br.bundled.money.business._0_domain.model.AccountType;
import br.bundled.money.business._1_application.AccountInput;
import br.bundled.money.web.book.Book;
import br.bundled.money.business._0_domain.model.Account;
import br.bundled.money.web.ui.Forms;
import br.bundled.money.web.ui.Ui;
import br.commons.Result;
import br.commons.lang.html.form.*;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * The fields of the account register, declared once: the view renders them and {@link #read} checks the
 * submission, plus the rules of INTENT §6: the parent is of the same family as the type, the name is unique among
 * its siblings, the type is fixed once there are transactions. The opening balance applies only on creation of a
 * financial account.
 */
@NullMarked
final class AccountFields {

    static final Field NAME = FieldSpec.of("name", InputType.TEXT).asRequired().withMaxLength(60).build().getOrThrow();
    static final Field TYPE = FieldSpec.of("type", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field PARENT = FieldSpec.of("parent", InputType.TEXT).asRequired().build().getOrThrow();
    static final Field OPENING = FieldSpec.of("opening", InputType.NUMBER).withStep("0.01").build().getOrThrow();
    static final Field TARGET = FieldSpec.of("target", InputType.TEXT).asRequired().build().getOrThrow();

    static final List<Field> ALL = List.of(NAME, TYPE, PARENT, OPENING);

    /** The types the register offers, in this order. */
    static final List<AccountType> TYPES = List.of(AccountType.BANK, AccountType.CREDIT, AccountType.CASH,
            AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY, AccountType.INCOME, AccountType.EXPENSE);

    private AccountFields() {}

    /** The type in the form, or the default for a new account. */
    static AccountType type(Map<String, String> values) {
        val token = values.getOrDefault(TYPE.name(), "");
        return TYPES.stream().filter(type -> type.name().equals(token)).findFirst().orElse(AccountType.BANK);
    }

    /** The checked values, or every violation found. {@code current} is the account being edited. */
    static Result<AccountInput, List<Violation>> read(Function<String, String> value, Book book, @Nullable Account current) {
        val violations = new ArrayList<>(Validation.check(ALL, name -> List.of(value.apply(name))));
        val type = typeOf(value, book, current);
        if (type == null) invalid(violations, TYPE);
        val parent = parentOf(value, book, type, current);
        if (parent == null) invalid(violations, PARENT);
        val name = value.apply(NAME.name()).strip();
        if (taken(book, parent, name, current)) invalid(violations, NAME);
        if (!violations.isEmpty() || type == null || parent == null) {
            return new Result.Failure<>(List.copyOf(violations));
        }
        return Result.success(new AccountInput(name, type, parent.identity(), opening(value, type, current)));
    }

    /** The types with an opening balance. */
    static final Set<AccountType> FINANCIAL = EnumSet.of(AccountType.BANK, AccountType.CASH, AccountType.CREDIT);

    /** The opening balance: only on creation of a financial account. */
    private static @Nullable BigDecimal opening(Function<String, String> value, AccountType type, @Nullable Account current) {
        return current == null && FINANCIAL.contains(type) ? decimal(value.apply(OPENING.name())) : null;
    }

    /** With transactions or subaccounts the type is fixed; otherwise the one chosen, if offered. */
    private static @Nullable AccountType typeOf(Function<String, String> value, Book book, @Nullable Account current) {
        if (current != null && book.fixedType(current)) return current.type();
        return TYPES.stream().filter(candidate -> candidate.name().equals(value.apply(TYPE.name()))).findFirst().orElse(null);
    }

    private static @Nullable Account parentOf(Function<String, String> value, Book book, @Nullable AccountType type,
                                                  @Nullable Account current) {
        if (type == null) return null;
        return book.account(Ui.uuid(value.apply(PARENT.name())))
                .filter(found -> book.parents(type, current).contains(found)).orElse(null);
    }

    /** Another account under {@code parent} already has this name. */
    private static boolean taken(Book book, @Nullable Account parent, String name, @Nullable Account current) {
        if (parent == null) return false;
        return book.children(parent).stream().filter(sibling -> !sibling.equals(current))
                .anyMatch(sibling -> sibling.name().equalsIgnoreCase(name));
    }

    /** {@code field} is invalid, unless it already has a violation. */
    private static void invalid(List<Violation> violations, Field field) {
        if (violations.stream().noneMatch(violation -> violation.field().equals(field.name()))) {
            violations.add(new Violation(field.name(), ValidationRule.INVALID));
        }
    }

    private static @Nullable BigDecimal decimal(String value) {
        return value.isBlank() ? null : new BigDecimal(value.strip());
    }

    static String message(Violation violation) {
        return Forms.violation(violation, INVALID_MESSAGES);
    }

    private static final Map<String, String> INVALID_MESSAGES = Map.of(
            NAME.name(), "Já existe uma conta com este nome aqui.",
            PARENT.name(), "Conta-mãe não aceita este tipo.",
            TARGET.name(), "Escolha a conta de destino.");

    /** The values of an account, by field name, as the form shows them. */
    static Map<String, String> values(Account account) {
        val values = new HashMap<String, String>();
        values.put(NAME.name(), account.name());
        values.put(TYPE.name(), account.type().name());
        val parent = account.parent();
        values.put(PARENT.name(), parent == null ? "" : parent.identity().toString());
        return values;
    }

    /** The submitted values, by field name, to show them again (with the errors, or after a change of type). */
    static Map<String, String> values(Function<String, String> value) {
        val values = new HashMap<String, String>();
        ALL.forEach(field -> values.put(field.name(), value.apply(field.name())));
        return values;
    }
}
