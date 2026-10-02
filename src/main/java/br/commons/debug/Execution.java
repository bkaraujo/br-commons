package br.commons.debug;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Strings;
import br.commons.chrono.Time;
import br.commons.logger.LogLevel;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

@NullMarked
public abstract class Execution {
    private Execution() {}

    public static <T> T nanos(Supplier<T> supplier) { return time(supplier, Time::nanos, "ns"); }
    public static <T> T millis(Supplier<T> supplier) { return time(supplier, Time::millis, "ms"); }

    /** Variante de caminho quente: {@code caller} é a classe (constante) de quem cronometra, e
     *  poupa o gate de resolver o chamador pela pilha quando há override de log por pacote. */
    public static <T> T nanos(String caller, Supplier<T> supplier) { return time(caller, supplier, Time::nanos, "ns"); }
    public static <T> T millis(String caller, Supplier<T> supplier) { return time(caller, supplier, Time::millis, "ms"); }

    public static <T> T time(Supplier<T> supplier, LongSupplier clock, String scale) { return  time(Strings.EMPTY, supplier, clock, scale); }
    public static <T> T time(String caller, Supplier<T> supplier, LongSupplier clock, String scale) {
        if (!Logger.enabled(LogLevel.DEBUG, caller)) return supplier.get();

        val label = Meta.method(supplier);
        val start = clock.getAsLong();
        try {
            return supplier.get();
        } finally {
            Logger.debug("%s time: %s %s", label, clock.getAsLong() - start, scale);
        }
    }

    public static void nanos(Runnable runnable) { time(runnable, Time::nanos, "ns"); }
    public static void millis(Runnable runnable) { time(runnable, Time::millis, "ms"); }

    public static void nanos(String caller, Runnable runnable) { time(caller, runnable, Time::nanos, "ns"); }
    public static void millis(String caller, Runnable runnable) { time(caller, runnable, Time::millis, "ms"); }

    public static void time(Runnable runnable, LongSupplier clock, String scale) { time(Strings.EMPTY, runnable, clock, scale); }
    public static void time(String caller, Runnable runnable, LongSupplier clock, String scale) {
        if (!Logger.enabled(LogLevel.DEBUG, caller)) {
            runnable.run();
            return;
        }

        val label = Meta.method(runnable);
        val start = clock.getAsLong();
        try {
            runnable.run();
        } finally {
            Logger.debug("%s time: %s %s", label, clock.getAsLong() - start, scale);
        }
    }
}
