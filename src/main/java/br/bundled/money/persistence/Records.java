package br.bundled.money.persistence;

import br.bundled.money.business._0_domain.DomainErrors;
import br.commons.Result;
import br.commons.lang.Json;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;

/** A record as a JSON field of the storage, and back; a field that cannot be read back is a failure, not a bug. */
@NullMarked
final class Records {

    private Records() {}

    static Result<Void, String> write(Storage storage, String file, String key, Record row) {
        return storage.write(file, key, Json.write(row).getBytes(StandardCharsets.UTF_8));
    }

    static <T extends Record> Result<T, String> read(Storage storage, String file, String key, Class<T> type) {
        return storage.read(file, key).flatMap(found -> found.map(content -> parse(content, file, key, type))
                .orElseGet(() -> Result.failure(unreadable(key, file, "∅"))));
    }

    private static <T extends Record> Result<T, String> parse(byte[] content, String file, String key, Class<T> type) {
        try {
            return Result.success(Json.from(new String(content, StandardCharsets.UTF_8)).as(type));
        } catch (RuntimeException e) {
            return Result.failure(unreadable(key, file, String.valueOf(e.getMessage())));
        }
    }

    static String unreadable(String key, String file, String cause) {
        val error = DomainErrors.rule(DomainErrors.STORAGE_UNREADABLE, key, file, cause);
        return error.render();
    }
}
