package br.commons.lang.html.form;

import br.commons.Result;
import br.commons.locale.i18n.Errors;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Type of an {@code <input>} and how the server reads its submitted value. How a type renders is up to the
 * {@code Dialect}: HTML 5 renders every type natively, HTML 4 falls back to {@code text}; the server reads the
 * value the same way in both, since the browser check can be bypassed or may not exist.
 *
 * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-type">HTML: the type attribute of input</a>
 * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-type-INPUT">HTML 4.01: the type attribute of INPUT</a>
 */
@NullMarked
public enum InputType {

    TEXT("text", InputKind.TEXTUAL, InputType::any),
    SEARCH("search", InputKind.TEXTUAL, InputType::any),
    TEL("tel", InputKind.TEXTUAL, InputType::any),
    PASSWORD("password", InputKind.TEXTUAL, InputType::any),
    EMAIL("email", InputKind.TEXTUAL, InputType::email),
    URL("url", InputKind.TEXTUAL, InputType::url),
    NUMBER("number", InputKind.ORDERED, InputType::number),
    RANGE("range", InputKind.ORDERED, InputType::number),
    DATE("date", InputKind.ORDERED, value -> Attempt.parse(value, LocalDate::parse)),
    TIME("time", InputKind.ORDERED, value -> Attempt.parse(value, LocalTime::parse)),
    DATETIME_LOCAL("datetime-local", InputKind.ORDERED, value -> Attempt.parse(value, LocalDateTime::parse)),
    MONTH("month", InputKind.ORDERED, value -> Attempt.parse(value, YearMonth::parse)),
    WEEK("week", InputKind.ORDERED, InputType::week),
    COLOR("color", InputKind.OTHER, InputType::color),
    HIDDEN("hidden", InputKind.OTHER, InputType::any),
    CHECKBOX("checkbox", InputKind.OTHER, InputType::any),
    RADIO("radio", InputKind.OTHER, InputType::any),
    FILE("file", InputKind.OTHER, InputType::any),
    SUBMIT("submit", InputKind.OTHER, InputType::any),
    RESET("reset", InputKind.OTHER, InputType::any),
    BUTTON("button", InputKind.OTHER, InputType::any),
    IMAGE("image", InputKind.OTHER, InputType::any);

    /** Valid e-mail address of the HTML spec (the {@code type=email} check). */
    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
            + "@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*");
    private static final Pattern WEEK_VALUE = Pattern.compile("(\\d{4,6})-W(\\d{2})");
    private static final Pattern COLOR_VALUE = Pattern.compile("#[0-9a-fA-F]{6}");

    private final String token;
    private final InputKind kind;
    private final Function<String, ? extends Result<? extends Comparable<?>, String>> reader;

    InputType(String token, InputKind kind, Function<String, ? extends Result<? extends Comparable<?>, String>> reader) {
        this.token = token;
        this.kind = kind;
        this.reader = reader;
    }

    /** The {@code type} attribute value in HTML 5. */
    public String token() {
        return token;
    }

    public InputKind kind() {
        return kind;
    }

    /** Free text: takes {@code minlength}, {@code maxlength} and {@code pattern}. */
    public boolean textual() {
        return kind == InputKind.TEXTUAL;
    }

    /** Ordered values: takes {@code min} and {@code max}. */
    public boolean ordered() {
        return kind == InputKind.ORDERED;
    }

    /**
     * The submitted (non-empty) value read by its meaning: {@link BigDecimal} for numbers, {@code java.time} for
     * dates and times, the text itself otherwise. Values of an {@linkplain #ordered() ordered} type compare
     * with each other ({@code 10 > 9}, {@code 2026-10-02 > 2026-09-30}).
     */
    public Result<Comparable<?>, String> read(String value) {
        return reader.apply(value).map(read -> read);
    }

    public boolean accepts(String value) {
        return read(value).isSuccess();
    }

    private static Result<String, String> any(String value) {
        return Result.success(value);
    }

    private static Result<String, String> email(String value) {
        return matching(EMAIL_ADDRESS, value, Errors.get().notAnEmail(value));
    }

    private static Result<String, String> url(String value) {
        return Attempt.parse(value, URI::create)
                .flatMap(uri -> uri.isAbsolute() ? any(value) : Result.failure(Errors.get().notAbsoluteUrl(value).value()));
    }

    /** A valid floating-point number of the HTML spec: {@link BigDecimal} syntax without a leading {@code +}. */
    private static Result<BigDecimal, String> number(String value) {
        return value.startsWith("+")
                ? Result.failure(Errors.get().leadingPlus(value).value())
                : Attempt.parse(value, BigDecimal::new);
    }

    /** {@code 2026-W40}; ISO weeks, so the year has 52 or 53. Equal-width values sort as text. */
    private static Result<String, String> week(String value) {
        val matcher = WEEK_VALUE.matcher(value);
        if (!matcher.matches()) {
            return Result.failure(Errors.get().notAWeek(value).value());
        }
        val year = Integer.parseInt(matcher.group(1));
        val week = Integer.parseInt(matcher.group(2));
        val weeks = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return week < 1 || week > weeks ? Result.failure(Errors.get().weekOutOfYear(String.valueOf(year), value).value()) : any(value);
    }

    private static Result<String, String> color(String value) {
        return matching(COLOR_VALUE, value, Errors.get().notAColor(value));
    }

    private static Result<String, String> matching(Pattern pattern, String value, Text error) {
        return pattern.matcher(value).matches() ? any(value) : Result.failure(error.value());
    }
}
