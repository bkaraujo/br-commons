package br.commons.http.server;

import br.commons.Strings;
import br.commons.http.Multipart;
import br.commons.http.Multipart.UploadedFile;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Streams a {@code multipart/form-data} body (RFC 7578) without holding it in memory (spec §5.2): each file goes to
 * a temporary file in {@code uploadDir}, counting its bytes and cutting at {@link Limits#maxFileSize()}; the text
 * fields stay in memory, each up to {@link #MAX_FIELD_SIZE}. Above {@link Limits#maxRequestSize()} the parse stops
 * with {@link TooLarge}, and the temporary files written so far are deleted.
 */
@NullMarked
public final class MultipartParser {

    /** Where the files go and how big they may be. */
    public record Limits(Path uploadDir, long maxFileSize, long maxRequestSize) {}

    /** The request body exceeds {@link Limits#maxRequestSize()}. */
    public static final class TooLarge extends IOException {
        private static final long serialVersionUID = 1L;

        TooLarge() {
            super("multipart body exceeds the request limit");
        }
    }

    static final int MAX_FIELD_SIZE = 64 * 1024;
    private static final int MAX_HEADER_SIZE = 8 * 1024;
    private static final Pattern BOUNDARY = Pattern.compile("(?i)boundary=(\"([^\"]+)\"|[^;\\s]+)");
    private static final Pattern PARAMETER = Pattern.compile("(?i);\\s*([a-z*]+)=(\"((?:[^\"\\\\]|\\\\.)*)\"|[^;]*)");

    private final Limits limits;

    public MultipartParser(Limits limits) {
        this.limits = limits;
    }

    /** Whether {@code contentType} announces a form-data body this parser reads. */
    public static boolean accepts(@Nullable String contentType) {
        return contentType != null && Strings.lower(contentType).startsWith("multipart/form-data");
    }

    public Multipart parse(@Nullable String contentType, InputStream body) throws IOException {
        val boundary = boundary(contentType);
        val input = new Counted(new BufferedInputStream(body, 64 * 1024), limits.maxRequestSize());
        val fields = new LinkedHashMap<String, List<String>>();
        val files = new ArrayList<UploadedFile>();
        try {
            skipPreamble(input, ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1));
            val delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            boolean last;
            do {
                val headers = headers(input);
                val disposition = parameters(headers.getOrDefault("content-disposition", ""));
                val name = disposition.getOrDefault("name", "");
                val filename = disposition.get("filename");
                if (filename == null) {
                    val value = new Capped(MAX_FIELD_SIZE);
                    copyUntil(input, delimiter, value);
                    fields.computeIfAbsent(name, k -> new ArrayList<>()).add(value.text());
                } else {
                    files.add(file(input, delimiter, name, filename, headers.get("content-type")));
                }
                last = afterDelimiter(input);
            } while (!last);
            return new Multipart(fields, files);
        } catch (IOException | RuntimeException e) {
            new Multipart(Map.of(), files).delete();
            throw e;
        }
    }

    private UploadedFile file(Counted input, byte[] delimiter, String name, String filename, @Nullable String contentType)
            throws IOException {
        val path = Files.createTempFile(limits.uploadDir(), "upload-", ".part");
        try (val out = Files.newOutputStream(path)) {
            val capped = new LimitedFile(out, limits.maxFileSize());
            val size = copyUntil(input, delimiter, capped);
            capped.flush();
            return new UploadedFile(name, filename, contentType, path, size, size > limits.maxFileSize());
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    static String boundary(@Nullable String contentType) throws IOException {
        val matcher = contentType == null ? null : BOUNDARY.matcher(contentType);
        if (matcher == null || !matcher.find()) throw new IOException("multipart body without boundary");
        val quoted = matcher.group(2);
        return quoted != null ? quoted : matcher.group(1);
    }

    /** Parameters of a header value ({@code form-data; name="file"; filename="a.pdf"}), names in lower case. */
    static Map<String, String> parameters(String header) {
        val result = new LinkedHashMap<String, String>();
        val matcher = PARAMETER.matcher(header);
        while (matcher.find()) {
            val quoted = matcher.group(3);
            result.put(Strings.lower(matcher.group(1)), quoted != null ? quoted.replaceAll("\\\\(.)", "$1") : matcher.group(2).strip());
        }
        return result;
    }

    private static void skipPreamble(Counted input, byte[] first) throws IOException {
        copyUntil(input, first, OutputStream.nullOutputStream());
    }

    /** Reads the part headers up to the blank line; names in lower case. */
    private static Map<String, String> headers(Counted input) throws IOException {
        val raw = new Capped(MAX_HEADER_SIZE);
        copyUntil(input, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), raw);
        val headers = new LinkedHashMap<String, String>();
        for (val line : raw.text().split("\r\n")) {
            val colon = line.indexOf(':');
            if (colon > 0) headers.put(Strings.lower(line.substring(0, colon).strip()), line.substring(colon + 1).strip());
        }
        return headers;
    }

    /** After a delimiter: {@code --} closes the body, CRLF starts another part. */
    private static boolean afterDelimiter(Counted input) throws IOException {
        val a = input.read();
        val b = input.read();
        if (a == '-' && b == '-') return true;
        if (a == '\r' && b == '\n') return false;
        throw new IOException("malformed multipart delimiter");
    }

    /**
     * Copies bytes to {@code out} until {@code delimiter} (which is consumed, not copied); returns how many bytes were
     * copied.
     */
    static long copyUntil(InputStream input, byte[] delimiter, OutputStream out) throws IOException {
        val matcher = new DelimiterMatcher(delimiter, out);
        while (true) {
            val read = input.read();
            if (read < 0) throw new IOException("multipart body ended before its delimiter");
            if (matcher.accept((byte) read)) return matcher.copied;
        }
    }

    /**
     * KMP over the delimiter: bytes that can no longer start it are written out, so the scan stays linear even when
     * the data repeats a prefix of the delimiter.
     */
    private static final class DelimiterMatcher {
        private final byte[] delimiter;
        private final int[] failure;
        private final OutputStream out;
        private int matched;
        long copied;

        DelimiterMatcher(byte[] delimiter, OutputStream out) {
            this.delimiter = delimiter;
            this.failure = failure(delimiter);
            this.out = out;
        }

        /** Takes the next byte; {@code true} once the whole delimiter was seen. */
        boolean accept(byte b) throws IOException {
            while (matched > 0 && delimiter[matched] != b) {
                val keep = failure[matched - 1];
                emit(delimiter, matched - keep);
                matched = keep;
            }
            if (delimiter[matched] != b) {
                out.write(b);
                copied++;
                return false;
            }
            return ++matched == delimiter.length;
        }

        private void emit(byte[] bytes, int length) throws IOException {
            out.write(bytes, 0, length);
            copied += length;
        }

        private static int[] failure(byte[] pattern) {
            val failure = new int[pattern.length];
            int k = 0;
            for (int i = 1; i < pattern.length; i++) {
                while (k > 0 && pattern[i] != pattern[k]) k = failure[k - 1];
                if (pattern[i] == pattern[k]) k++;
                failure[i] = k;
            }
            return failure;
        }
    }

    /** Counts every byte read and fails once the request limit is passed. */
    private static final class Counted extends InputStream {
        private final InputStream in;
        private final long limit;
        private long count;

        Counted(InputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            val b = in.read();
            if (b >= 0 && ++count > limit) throw new TooLarge();
            return b;
        }
    }

    /** A text value kept in memory; beyond its cap the part is malformed for this application. */
    private static final class Capped extends ByteArrayOutputStream {
        private final int cap;

        Capped(int cap) {
            this.cap = cap;
        }

        @Override
        public void write(int b) {
            if (count >= cap) throw new IllegalStateException("multipart field or header exceeds " + cap + " bytes");
            super.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) write(b[off + i]);
        }

        String text() {
            return toString(StandardCharsets.UTF_8);
        }
    }

    /** Writes up to {@code limit} bytes and silently drops the rest; the caller sees the full count. */
    private static final class LimitedFile extends OutputStream {
        private final OutputStream out;
        private final long limit;
        private long written;

        LimitedFile(OutputStream out, long limit) {
            this.out = new BufferedOutputStream(out, 64 * 1024);
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            if (written++ < limit) out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) write(b[off + i]);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }
    }
}
