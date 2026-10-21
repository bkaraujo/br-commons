package br.commons.persistence.inmemory;

import br.commons.Logger;
import br.commons.Result;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@NullMarked
public class InMemoryStorage implements Storage {

    private final Map<String, Map<String, byte[]>> data = new ConcurrentHashMap<>();

    @Override
    public Result<Optional<byte[]>, String> read(String file, String jsonKey) {
        Logger.info("Reading %s from %s", jsonKey, file);
        val fileContent = data.get(file);
        return Result.success(fileContent == null ? Optional.empty() : Optional.ofNullable(fileContent.get(jsonKey)));
    }

    @Override
    public Result<Void, String> write(String file, String jsonKey, byte[] content) {
        Logger.trace("Writing %s content to %s", jsonKey, file);
        data.computeIfAbsent(file, k -> new ConcurrentHashMap<>()).put(jsonKey, content);
        return Result.success();
    }

    /** The last field removed takes the file with it, as if it had never been written. */
    @Override
    public Result<Void, String> delete(String file, String jsonKey) {
        data.computeIfPresent(file, (_, fields) -> {
            Logger.trace("Deleting %s from %s", fields, file);
            fields.remove(jsonKey);
            return fields.isEmpty() ? null : fields;
        });
        return Result.success();
    }

    @Override
    public Result<List<String>, String> keys(String file) {
        Logger.trace("Reading keys from %s", file);
        val fields = data.get(file);
        return Result.success(fields == null ? List.of() : List.copyOf(fields.keySet()));
    }

    @Override
    public boolean exists(String file) {
        return data.containsKey(file);
    }

    @Override
    public List<String> listFiles(String prefix, String suffix) {
        return data.keySet().stream()
                .filter(name -> (prefix == null || name.startsWith(prefix)) && (suffix == null || name.endsWith(suffix)))
                .collect(Collectors.toList());
    }

    public void clear() {
        data.clear();
    }
}
