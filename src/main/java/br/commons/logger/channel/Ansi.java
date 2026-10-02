package br.commons.logger.channel;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Writes a line removing ANSI sequences (CSI {@code ESC[...m}) — logic shared
 * by file channels ({@link FileChannel} and the rolling writer), which should not write colors.
 */
@NullMarked
final class Ansi {

    /** ESC (0x1B): beginning of every ANSI sequence. */
    private static final char ESCAPE = 0x1B;

    private Ansi() {
    }

    static void writeStripped(BufferedWriter writer, String message) throws IOException {
        val length = message.length();
        int lastWritePos = 0;
        int currentPos = 0;

        while (currentPos < length) {
            if (message.charAt(currentPos) != ESCAPE) {
                currentPos++;
                continue;
            }
            val len = currentPos - lastWritePos;
            if (len > 0) {
                writer.write(message, lastWritePos, len);
            }
            val end = sequenceEnd(message, currentPos, length);
            if (end < 0) {
                currentPos++;
                continue;
            }
            currentPos = end;
            lastWritePos = currentPos;
        }

        val tailLen = length - lastWritePos;
        if (tailLen > 0) {
            writer.write(message, lastWritePos, tailLen);
        }
        writer.newLine();
    }

    /** Index right after {@code ESC[...m}, or {@code -1} if not a CSI (ESC isolated). */
    private static int sequenceEnd(String message, int escPos, int length) {
        int end = escPos + 1;
        if (end >= length || message.charAt(end) != '[') {
            return -1;
        }
        end++;
        while (end < length && message.charAt(end++) != 'm') {
            /* advance to the 'm' terminator */
        }
        return end;
    }
}
