package br.commons.cdi;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Threads;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * <p>
 *     The Context class is a utility for managing singleton instances of various types
 *     in a thread-safe manner. It provides methods for storing, retrieving, and removing
 *     instances associated with specific types.
 *</p>
 * <p>
 *     Reads ({@link #get}) are lock-free; writes and {@link #tryGet} (get-or-create, which must not create
 *     twice) are serialized by a lock.
 * </p>
 */
@NullMarked
public abstract class Context {

    private static final ReentrantLock lock = new ReentrantLock();
    private static final Map<Class<?>, Object> registry = new ConcurrentHashMap<>();

    private Context(){}

    /** Lock-free read: the registry is a {@link ConcurrentHashMap}, and {@link #set} only publishes ready instances. */
    public static <T> T get(Class<T> type) {
        val instance = registry.get(type);
        if (instance == null) {
            throw new IllegalStateException("No instance of %s registered".formatted(type));
        }

        return type.cast(instance);
    }

    /** The supplier runs outside the lock (it may be slow or touch the context itself); only the publication is locked. */
    public static <T> void set(Class<T> type, Supplier<T> instance) {
        val created = instance.get();
        Threads.locked(lock, () -> {
            Logger.debug("Registering %s", type);
            registry.put(type, created);
        });
    }

    public static <T> void set(Class<T> type) {
        set(type, () -> Meta.instance(type));
    }

    public static void remove(Class<?> type) {
        Threads.locked(lock, () -> {
            Logger.debug("Removing %s", type);
            registry.remove(type);
        });
    }

    public static void clear() {
        Logger.debug("Clearing registry");
        Threads.locked(lock, registry::clear);
    }


    public static <T> T tryGet(Class<T> type) {
        return tryGet(type, () -> Meta.instance(type));
    }

    public static <T> T tryGet(Class<T> type, Supplier<T> supplier) {
        return Threads.locked(lock, () -> {
            var instance = registry.get(type);
            if (instance == null) {
                instance = supplier.get();
                Logger.verbose("Registering %s", type);
                registry.put(type, instance);
            }

           return type.cast(instance);
        });
    }
}
