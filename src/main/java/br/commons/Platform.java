package br.commons;

import br.commons.locale.i18n.Errors;

import br.commons.platform.FileSystem;
import br.commons.platform.Network;
import br.commons.platform.OS;
import br.commons.platform.Terminal;
import br.commons.platform.provider.linux.LNXFileSystem;
import br.commons.platform.provider.linux.LNXNetwork;
import br.commons.platform.provider.linux.LNXTerminal;
import br.commons.platform.provider.windows.WINFileSystem;
import br.commons.platform.provider.windows.WINNetwork;
import br.commons.platform.provider.windows.WINTerminal;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class Platform {

    private Platform(){}


    public static final OS CURRENT;
    static {
        val osName = Strings.lower(System.getProperty("os.name"));
        if (osName.contains("win")) {
            CURRENT = OS.WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            CURRENT = OS.LINUX;
        } else if (osName.contains("mac")) {
            CURRENT = OS.MAC;
        } else {
            CURRENT = OS.UNKNOWN;
        }
    }

    /**
     * Holder idiom: each service is created once, by the JVM's class initialization (thread-safe, no lock on
     * later calls). {@code null} = OS without a provider, reported by {@link #unsupported}.
     */
    private static final class FileSystemHolder {
        static final @Nullable FileSystem INSTANCE = switch (CURRENT) {
            case LINUX -> new LNXFileSystem();
            case WINDOWS -> new WINFileSystem();
            default -> null;
        };
    }

    private static final class TerminalHolder {
        static final @Nullable Terminal INSTANCE = switch (CURRENT) {
            case LINUX -> new LNXTerminal();
            case WINDOWS -> new WINTerminal();
            default -> null;
        };
    }

    private static final class NetworkHolder {
        static final @Nullable Network INSTANCE = switch (CURRENT) {
            case LINUX -> new LNXNetwork();
            case WINDOWS -> new WINNetwork();
            default -> null;
        };
    }

    /** @throws UnsupportedOperationException on an OS without a provider (e.g. macOS) */
    public static FileSystem fileSystem() {
        val instance = FileSystemHolder.INSTANCE;
        if (instance == null) throw unsupported("FileSystem");
        return instance;
    }

    /** @throws UnsupportedOperationException on an OS without a provider (e.g. macOS) */
    public static Terminal terminal() {
        val instance = TerminalHolder.INSTANCE;
        if (instance == null) throw unsupported("Terminal");
        return instance;
    }

    /** @throws UnsupportedOperationException on an OS without a provider (e.g. macOS) */
    public static Network network() {
        val instance = NetworkHolder.INSTANCE;
        if (instance == null) throw unsupported("Network");
        return instance;
    }

    private static UnsupportedOperationException unsupported(String service) {
        return new UnsupportedOperationException(
                Errors.get().platformUnsupported(service, CURRENT, String.valueOf(System.getProperty("os.name"))).value());
    }

    /** Verifica se a porta TCP já está em uso no host local. Ver {@link Network#isPortInUse(int)}. */
    public static boolean isPortInUse(int port) {
        return network().isPortInUse(port);
    }

}
