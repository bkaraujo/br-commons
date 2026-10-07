package br.commons.protocol.http;

import br.commons.protocol.http.server.MultipartParser;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A {@code multipart/form-data} body as the {@link MultipartParser} streamed it:
 * the text fields in memory and every file in a temporary file of the upload directory, never the whole body in
 * memory. The server deletes the files when the request ends, with success or error.
 */
@NullMarked
public record Multipart(Map<String, List<String>> fields, List<UploadedFile> files) {

    public static final Multipart EMPTY = new Multipart(Map.of(), List.of());

    public Multipart {
        fields = Map.copyOf(fields);
        files = List.copyOf(files);
    }

    /** The first value of the field, or {@code null}. */
    public @Nullable String field(String name) {
        val values = fields.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    public List<UploadedFile> files(String name) {
        return files.stream().filter(file -> file.name().equals(name)).toList();
    }

    /** Deletes every temporary file; safe to call more than once. */
    public void delete() {
        for (val file : files) {
            try {
                Files.deleteIfExists(file.path());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * A file part. {@code size} counts every byte the client sent; when it exceeds the limit, {@code tooLarge} is set
     * and {@code path} holds only the bytes up to the limit, which the caller must reject.
     */
    public record UploadedFile(String name, String filename, @Nullable String contentType, Path path, long size,
                               boolean tooLarge) {}
}
