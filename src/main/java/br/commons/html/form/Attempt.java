package br.commons.html.form;

import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.time.DateTimeException;
import java.util.function.Function;

/**
 * The JDK parsers ({@code LocalDate.parse}, {@code new BigDecimal}, {@code Pattern.compile}) fail by exception;
 * this is the only place in the package that catches one, turning it into a {@link Result}.
 */
@NullMarked
final class Attempt {

    private Attempt() {}

    static <T> Result<T, String> parse(String text, Function<String, T> parser) {
        try {
            return Result.success(parser.apply(text));
        } catch (IllegalArgumentException | DateTimeException invalid) {
            return Result.failure("Invalid value " + text + ": " + invalid.getMessage());
        }
    }
}
