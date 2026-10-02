package logsample.alpha;

import br.commons.Logger;
import org.jspecify.annotations.NullMarked;

/** Log emitter in a distinct package, to exercise per-package level overrides. */
@NullMarked
public final class Alpha {
    private Alpha() {}

    public static void debug(String message) { Logger.debug(message); }
    public static void info(String message)  { Logger.info(message); }
    public static void warn(String message)  { Logger.warn(message); }
}
