package br.commons.persistence.json;

import br.commons.Logger;
import br.commons.Platform;
import br.commons.Result;
import br.commons.Threads;
import br.commons.locale.i18n.Errors;
import br.commons.persistence.Storage;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * JSON files on disk, one object per file, one field per key. {@link #write} is a read-modify-write of the whole
 * file: serialized per file (lock shared by every instance of the JVM) and published by an atomic move of a
 * temporary file, so concurrent writers do not lose fields and a crash never leaves a half-written file.
 */
@NullMarked
public final class LocalFileStorage implements Storage {

    /** One lock per file, shared by every instance: two storages over the same root must not interleave. */
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private final Path root;
    private final ObjectMapper mapper;

    public LocalFileStorage(String path, ObjectMapper mapper) {
        this.root = Platform.fileSystem().jvmFS().resolve(path).toAbsolutePath().normalize();
        this.mapper = mapper;
        Logger.info("datafiles: %s", root);
    }

    @Override
    public Result<Optional<byte[]>, String> read(String file, String jsonKey) {
        return resolve(file).flatMap(path -> {
            if (!Files.exists(path)) return Result.success(Optional.empty());
            try {
                val rootNode = mapper.readTree(path.toFile());
                val field = rootNode.get(jsonKey);
                if (field == null || field.isNull()) return Result.success(Optional.empty());
                return Result.success(Optional.of(mapper.writeValueAsBytes(field)));
            } catch (JacksonException e) {
                return Result.failure(Errors.get().fieldReadFailed(jsonKey, file, String.valueOf(e.getMessage())).value());
            }
        });
    }

    @Override
    public Result<Void, String> write(String file, String jsonKey, byte[] content) {
        return resolve(file).flatMap(path ->
                Threads.locked(LOCKS.computeIfAbsent(path, _ -> new ReentrantLock()), () -> rewrite(path, file, jsonKey, content)));
    }

    private Result<Void, String> rewrite(Path path, String file, String jsonKey, byte[] content) {
        try {
            Files.createDirectories(path.getParent());

            ObjectNode rootNode;
            if (Files.exists(path)) {
                rootNode = (ObjectNode) mapper.readTree(path.toFile());
            } else {
                rootNode = mapper.createObjectNode();
            }

            val newNode = mapper.readTree(content);
            rootNode.set(jsonKey, newNode);

            val temporary = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), rootNode);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return Result.success();
        } catch (IOException | JacksonException e) {
            return Result.failure(Errors.get().fieldWriteFailed(jsonKey, file, String.valueOf(e.getMessage())).value());
        }
    }

    @Override
    public boolean exists(String file) {
        return resolve(file).map(Files::exists).getOrElse(false);
    }

    @Override
    public List<String> listFiles(String prefix, String suffix) {
        if (!Files.exists(root)) return List.of();
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(prefix) && n.endsWith(suffix))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(Errors.get().listFilesFailed(root).value(), e);
        }
    }

    /**
     * The file under the root, or {@code Failure} when the name leaves it ({@code ..}, absolute path, another
     * drive): {@code file} may come from a request (path traversal).
     */
    private Result<Path, String> resolve(String file) {
        try {
            val path = root.resolve(file).normalize();
            if (!path.startsWith(root)) return Result.failure(Errors.get().fileOutsideStorage(file).value());
            return Result.success(path);
        } catch (InvalidPathException e) {
            return Result.failure(Errors.get().invalidFileName(file).value());
        }
    }
}
