package br.commons.platform.provider.linux;

import br.commons.Logger;
import br.commons.platform.Network;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Linux implementation of Network.
 * Reads {@code /proc/net/tcp} and {@code /proc/net/tcp6} and considers only sockets in
 * LISTEN ({@code st == 0A}), ignoring transient states like TIME_WAIT.
 */
@NullMarked
public class LNXNetwork implements Network {

    /** Code of the TCP_LISTEN state in the {@code st} column of /proc/net/tcp. */
    private static final String TCP_LISTEN = "0A";
    private static final Path PROC_TCP  = Path.of("/proc/net/tcp");
    private static final Path PROC_TCP6 = Path.of("/proc/net/tcp6");

    @Override
    public boolean isPortInUse(int port) {
        return hasListener(PROC_TCP, port) || hasListener(PROC_TCP6, port);
    }

    private boolean hasListener(Path procFile, int port) {
        if (!Files.exists(procFile)) return false;

        try {
            for (val line : Files.readAllLines(procFile)) {
                if (matchesListener(line, port)) return true;
            }
        } catch (IOException exception) {
            Logger.warn("Failed to read %s: %s", procFile, String.valueOf(exception.getMessage()));
        }
        return false;
    }

    // Line: "  sl  local_address rem_address st ..."
    // Field 1 = local_address ("0100007F:1538"); field 3 = st ("0A" = LISTEN).
    // The header does not match: its field 3 is the string "st", not "0A".
    private boolean matchesListener(String line, int port) {
        val fields = line.trim().split("\\s+");
        if (fields.length < 4 || !TCP_LISTEN.equals(fields[3])) return false;

        val colon = fields[1].lastIndexOf(':');
        if (colon < 0) return false;

        try {
            return Integer.parseInt(fields[1].substring(colon + 1), 16) == port;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
