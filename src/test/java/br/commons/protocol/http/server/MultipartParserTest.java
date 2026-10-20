package br.commons.protocol.http.server;

import br.commons.protocol.http.Multipart;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Streaming multipart reading (spec §5.2): files to disk, limits, malformed bodies. */
class MultipartParserTest {

    private static final String BOUNDARY = "----formBoundary7MA4YWxk";
    private static final String TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    @TempDir
    Path dir;

    private MultipartParser parser(long maxFile, long maxRequest) {
        return new MultipartParser(new MultipartParser.Limits(dir, maxFile, maxRequest));
    }

    /** Builds a body: each entry is {name, filename-or-null, content}. */
    private static byte[] body(Object[]... parts) {
        val out = new ByteArrayOutputStream();
        out.writeBytes("preamble ignored\r\n".getBytes(StandardCharsets.ISO_8859_1));
        for (val part : parts) {
            val filename = part[1] == null ? "" : "; filename=\"" + part[1] + "\"\r\nContent-Type: application/pdf";
            out.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + part[0] + "\"" + filename + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.writeBytes(part[2] instanceof byte[] bytes ? bytes : ((String) part[2]).getBytes(StandardCharsets.UTF_8));
            out.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        out.writeBytes(("--" + BOUNDARY + "--\r\nepilogue").getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private Multipart parse(MultipartParser parser, byte[] body) throws IOException {
        return parser.parse(TYPE, new ByteArrayInputStream(body));
    }

    @Test
    void readsFieldsInMemoryAndFilesToDisk() throws IOException {
        val pdf = "%PDF-1.7\r\n--not-the-boundary\r\n\r\nç".getBytes(StandardCharsets.UTF_8);
        val result = parse(parser(1000, 10_000), body(
                new Object[]{"password", null, "s3nh@ çã"},
                new Object[]{"file", "extrato.pdf", pdf},
                new Object[]{"file", "fatura janeiro.pdf", new byte[0]}));

        assertEquals("s3nh@ çã", result.field("password"));
        assertNull(result.field("missing"));
        val files = result.files("file");
        assertEquals(2, files.size());
        assertEquals("extrato.pdf", files.get(0).filename());
        assertEquals("application/pdf", files.get(0).contentType());
        assertArrayEquals(pdf, Files.readAllBytes(files.get(0).path()));
        assertFalse(files.get(0).tooLarge());
        assertEquals("fatura janeiro.pdf", files.get(1).filename());
        assertEquals(0, files.get(1).size());

        result.delete();
        assertFalse(Files.exists(files.get(0).path()));
        assertDoesNotThrow(result::delete, "deleting twice is harmless");
    }

    @Test
    void cutsAFileAtItsLimitButCountsEveryByte() throws IOException {
        val result = parse(parser(4, 10_000), body(new Object[]{"file", "big.pdf", "0123456789"}));
        val file = result.files("file").getFirst();
        assertTrue(file.tooLarge());
        assertEquals(10, file.size());
        assertEquals("0123", Files.readString(file.path()));
    }

    @Test
    void stopsAboveTheRequestLimitAndLeavesNoFileBehind() throws IOException {
        val parser = parser(1000, 200);
        assertThrows(MultipartParser.TooLarge.class,
                () -> parse(parser, body(new Object[]{"a", "a.pdf", "x".repeat(100)}, new Object[]{"b", "b.pdf", "y".repeat(100)})));
        try (val left = Files.list(dir)) {
            assertEquals(0, left.count());
        }
    }

    @Test
    void rejectsMalformedBodies() {
        val parser = parser(1000, 10_000);
        assertThrows(IOException.class, () -> parser.parse("multipart/form-data", new ByteArrayInputStream(new byte[0])), "no boundary");
        assertThrows(IOException.class, () -> parse(parser, ("--" + BOUNDARY + "\r\n\r\nunterminated").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> parse(parser, ("--" + BOUNDARY + "\r\n\r\nx\r\n--" + BOUNDARY + "XX").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class,
                () -> parse(parser(1000, 1_000_000), body(new Object[]{"huge", null, "z".repeat(MultipartParser.MAX_FIELD_SIZE + 1)})));
    }

    @Test
    void readsQuotedBoundariesAndHeaderParameters() throws IOException {
        assertEquals("abc", MultipartParser.boundary("multipart/form-data; boundary=\"abc\""));
        assertEquals("abc", MultipartParser.boundary("Multipart/Form-Data; charset=utf-8; BOUNDARY=abc"));
        assertEquals(Map.of("name", "f", "filename", "a;\"b\".pdf"), MultipartParser.parameters("form-data; name=f; filename=\"a;\\\"b\\\".pdf\""));
        assertTrue(MultipartParser.accepts("Multipart/Form-Data; boundary=x"));
        assertFalse(MultipartParser.accepts("application/x-www-form-urlencoded"));
        assertFalse(MultipartParser.accepts(null));
    }

    @Test
    void findsTheDelimiterAfterAPartialRepeat() throws IOException {
        val out = new ByteArrayOutputStream();
        val copied = MultipartParser.copyUntil(new ByteArrayInputStream("aaabaaabaab!".getBytes(StandardCharsets.ISO_8859_1)),
                "aab!".getBytes(StandardCharsets.ISO_8859_1), out);
        assertEquals("aaabaaab", out.toString(StandardCharsets.ISO_8859_1));
        assertEquals(8, copied);
    }
}
