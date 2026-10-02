package br.commons.platform;

import org.jspecify.annotations.NullMarked;

/**
 * Platform-agnostic network interface.
 * Provides native TCP port inspection.
 */
@NullMarked
public interface Network {

    /**
     * Checks if the TCP port is in use (socket in LISTEN state) on the local host.
     * <p>
     * Native query of the operating system's TCP connection table, considering
     * only LISTEN sockets. Transient states (e.g.: {@code TIME_WAIT}) are ignored,
     * because they do not represent an active server on the port.
     *
     * @param port TCP port to check
     * @return {@code true} if there is an active listener on the port
     */
    boolean isPortInUse(int port);
}
