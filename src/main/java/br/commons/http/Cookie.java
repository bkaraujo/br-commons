package br.commons.http;

import br.commons.locale.i18n.Errors;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * A {@code Set-Cookie}. The application's session cookie (spec §4.1) is {@link #session}: {@code HttpOnly},
 * {@code Secure}, {@code SameSite=Strict}, {@code Path=/}, with no {@code Max-Age}.
 */
@NullMarked
public record Cookie(String name, String value, String path, @Nullable Duration maxAge, boolean httpOnly,
                     boolean secure, String sameSite) {

    /** CR/LF would split the header; {@code ;} would add attributes to it. */
    public Cookie {
        for (val part : new String[] {name, value, path, sameSite}) {
            if (part.indexOf('\r') >= 0 || part.indexOf('\n') >= 0 || part.indexOf(';') >= 0) {
                throw new IllegalArgumentException(Errors.get().invalidCookie(name.strip()).value());
            }
        }
    }

    public static Cookie session(String name, String value) {
        return new Cookie(name, value, "/", null, true, true, "Strict");
    }

    /** Asks the browser to discard the cookie. */
    public static Cookie expired(String name) {
        return new Cookie(name, "", "/", Duration.ZERO, true, true, "Strict");
    }

    /** Value of the {@code Set-Cookie} header. */
    public String header() {
        val header = new StringBuilder(name).append('=').append(value).append("; Path=").append(path);
        if (maxAge != null) header.append("; Max-Age=").append(maxAge.toSeconds());
        if (httpOnly) header.append("; HttpOnly");
        if (secure) header.append("; Secure");
        return header.append("; SameSite=").append(sameSite).toString();
    }
}
