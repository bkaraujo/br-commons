package br.commons.business;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

@Getter
@NullMarked
public class BusinessException extends RuntimeException {

    /** Locale for logging/development — {@link #getMessage()} is for stack trace and internal logs, not for
     *  HTTP response (whoever answers resolves {@link BusinessError#render(java.util.Locale)} in the request's locale).
     *  Team and logs are pt-BR, fixed, regardless of who asked. */
    private static final Locale LOG_LOCALE = Locale.of("pt", "BR");

    private final BusinessError error;

    public BusinessException(BusinessError error) {
        super(error.render(LOG_LOCALE));
        this.error = error;
    }
}
