package br.commons.platform.provider.agnostic;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

@NullMarked
public abstract class Network {

    private Network() {}

    /**
     * Checks if the TCP port is already in use on the local host.
     * <p>
     * 100% Java implementation (ServerSocket bind attempt),
     * portable across operating systems — does not require OS-specific provider.
     *
     * @param port TCP port to check
     * @return {@code true} if the port is already in use (bind failed)
     */
    public static boolean isPortInUse(int port) {
        try (val socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port));
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }
}
