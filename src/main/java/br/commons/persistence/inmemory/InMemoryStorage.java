package br.commons.persistence.inmemory;

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
        val fileContent = data.get(file);
        return Result.success(fileContent == null ? Optional.empty() : Optional.ofNullable(fileContent.get(jsonKey)));
    }

    @Override
    public Result<Void, String> write(String file, String jsonKey, byte[] content) {
        data.computeIfAbsent(file, k -> new ConcurrentHashMap<>()).put(jsonKey, content);
        return Result.success();
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
