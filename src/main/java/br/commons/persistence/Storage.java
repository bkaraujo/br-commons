package br.commons.persistence;

import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * Key/value fields grouped in named files. {@code file} may come from outside (a request): an implementation that
 * maps it to a path refuses names that leave its root with {@code Failure} instead of touching the disk.
 */
@NullMarked
public interface Storage {

    /** The field's bytes, empty when the file or the field does not exist. */
    Result<Optional<byte[]>, String> read(String file, String jsonKey);

    Result<Void, String> write(String file, String jsonKey, byte[] content);

    /** {@code false} also for a name the storage refuses. */
    boolean exists(String file);

    List<String> listFiles(String prefix, String suffix);
}
