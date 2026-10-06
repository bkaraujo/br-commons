package br.commons.message;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Strings;
import br.commons.Threads;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static br.commons.Logger.lazy;

@NullMarked
public abstract class MessageBus {

    private MessageBus() { /* Private constructor */ }

    private static final List<MessageProcessor> EMPTY = List.of();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    /** Reentrant: {@link #subscribe(Class)} and {@link #unsubscribe(Class)} call their {@code Object} overloads under it. */
    private static final Lock LOCK = new ReentrantLock();

    /**
     * Immutable snapshot of the registered processors plus the dispatch cache derived from it.
     * {@link #subscribe} and {@link #unsubscribe} swap the whole snapshot under {@link #LOCK}; {@link #submit} reads it once,
     * so dispatch never observes a half-registered container nor caches a list built from a stale map.
     */
    private record Registry(
            Map<Class<?>, List<MessageProcessor>> processors,
            ConcurrentMap<Class<?>, List<MessageProcessor>> dispatchCache
    ) {}

    private static volatile Registry registry = new Registry(Map.of(), new ConcurrentHashMap<>());

    /** Subscribed instances, by identity — two instances of the same class are two subscriptions. */
    private static final Map<Object, Integer> containers = new IdentityHashMap<>();
    /** Instances created by {@link #subscribe(Class)}, so {@link #unsubscribe(Class)} can find them. */
    private static final Map<Class<?>, Object> containerTypes = new HashMap<>();

    /** Clears all subscriptions and caches. Intended for test isolation only. */
    public static void reset() {
        Threads.locked(LOCK, () -> {
            registry = new Registry(Map.of(), new ConcurrentHashMap<>());
            containers.clear();
            containerTypes.clear();
        });
    }

    public static int subscribe(Class<?> container) {
        return Threads.locked(LOCK, () -> {
            val previous = containerTypes.get(container);
            if (previous != null) {
                Logger.warn("Attempt to resubscribe %s", Meta.fqn(container));
                return containers.getOrDefault(previous, 0);
            }
            val instance = Meta.instance(container);
            containerTypes.put(container, instance);
            return subscribe(instance);
        });
    }

    public static int subscribe(Object container) {
        return Threads.locked(LOCK, () -> {
            val fqn = Meta.fqn(container);
            val previous = containers.get(container);
            if (previous != null) {
                Logger.warn("Attempt to resubscribe %s", fqn);
                return previous;
            }

            val processors = new HashMap<Class<?>, List<MessageProcessor>>();
            registry.processors().forEach((type, list) -> processors.put(type, new ArrayList<>(list)));

            int subscribed = 0;
            Logger.verbose("Processing %s", fqn);
            for (val method : container.getClass().getMethods()) {
                subscribed += tryRegister(container, method, processors);
            }

            if (subscribed > 0) { publish(processors); }

            containers.put(container, subscribed);
            return subscribed;
        });
    }

    /** Removes the instance {@link #subscribe(Class)} created for {@code container}; returns the listeners removed. */
    public static int unsubscribe(Class<?> container) {
        return Threads.locked(LOCK, () -> {
            val instance = containerTypes.get(container);
            if (instance == null) {
                Logger.warn("Attempt to unsubscribe %s, which is not subscribed", Meta.fqn(container));
                return 0;
            }
            return unsubscribe(instance);
        });
    }

    /**
     * Removes every listener of {@code container} (matched by identity); returns how many were removed.
     * A {@link #submit} already running keeps dispatching to the snapshot it started with.
     */
    public static int unsubscribe(Object container) {
        return Threads.locked(LOCK, () -> {
            val fqn = Meta.fqn(container);
            val subscribed = containers.remove(container);
            if (subscribed == null) {
                Logger.warn("Attempt to unsubscribe %s, which is not subscribed", fqn);
                return 0;
            }
            containerTypes.values().removeIf(instance -> instance == container);

            if (subscribed > 0) {
                val processors = new HashMap<Class<?>, List<MessageProcessor>>();
                registry.processors().forEach((type, list) -> {
                    val kept = list.stream().filter(processor -> processor.container() != container).toList();
                    if (!kept.isEmpty()) { processors.put(type, kept); }
                });
                publish(processors);
            }

            Logger.verbose("Unsubscribed %s (%s listeners)", fqn, subscribed);
            return subscribed;
        });
    }

    /** Freezes {@code processors} into a new {@link Registry}; must run under {@link #LOCK}. */
    private static void publish(Map<Class<?>, List<MessageProcessor>> processors) {
        val frozen = new HashMap<Class<?>, List<MessageProcessor>>();
        processors.forEach((type, list) -> frozen.put(type, List.copyOf(list)));
        // Fresh cache: lists built from the previous snapshot die with it
        registry = new Registry(Map.copyOf(frozen), new ConcurrentHashMap<>());
    }

    /** Validates and registers a method annotated with {@link MessageListener}; returns 1 if registered, 0 otherwise. */
    private static int tryRegister(Object container, Method method, Map<Class<?>, List<MessageProcessor>> processors) {
        val annotation = method.getAnnotation(MessageListener.class);
        if (annotation == null) { return 0; }

        val parameters = method.getParameters();
        if (method.getReturnType() != MessageResult.class) {
            Logger.warn(
                    "Method '%s.%s' must return %s",
                    Meta.fqn(container),
                    method.getName(),
                    Meta.fqn(MessageResult.class)
            );
            return 0;
        }

        if (parameters.length != 1) {
            Logger.warn(
                    "Method '%s.%s' must accept a single parameter",
                    Meta.fqn(container),
                    method.getName()
            );
            return 0;
        }

        if (!Meta.assignable(parameters[0].getType(), Message.class)) {
            Logger.warn(
                    "Method '%s.%s' parameter must be assignable to %s",
                    Meta.fqn(container),
                    method.getName(),
                    Meta.fqn(Message.class)
            );

            return 0;
        }

        try { method.setAccessible(true); }
        catch (final SecurityException e) {
            Logger.warn(
                    "Failed to make '%s.%s' accessible'",
                    Meta.fqn(container),
                    method.getName()
            );

            return 0;
        }

        try {
            val handle = LOOKUP.unreflect(method);
            processors
                    .computeIfAbsent(parameters[0].getType(), ignored -> new ArrayList<>())
                    .add(new MessageProcessor(container, handle));

            Logger.verbose("Method accepted: %s(%s)", Logger.lazy(method::getName), Logger.lazy(() -> Meta.fqn(parameters[0].getType())));
            return 1;
        } catch (IllegalAccessException e) {
            Logger.warn(
                    "Failed to create handle for '%s.%s': %s",
                    Meta.fqn(container),
                    method.getName(),
                    Strings.orEmpty(e.getMessage())
            );
            return 0;
        }
    }

    /**
     * Dispatches to all listeners registered in the hierarchy of {@code message}, always — the
     * returning {@link MessageResult} no longer interrupts fan-out (no publisher relies on
     * "first handler wins"; multiple best-effort consumers of the same event is the common case).
     * An exception from any listener propagates directly from here (it is not swallowed): the publisher decides
     * what to do — typically roll back its own transaction, since {@code DataSource} uses
     * REQUIRED propagation per thread.
     */
    public static <T extends Message> void submit(T message) {
        val messageClass = message.getClass();
        Logger.verbose("Dispatching %s", lazy(() -> Meta.fqn(messageClass)));
        val current = registry;
        val processorList = current.dispatchCache()
                .computeIfAbsent(messageClass, type -> buildDispatchList(current.processors(), type));

        for (val processor : processorList) {
            processor.process(message);
        }
    }

    /**
     * Listeners of every supertype of {@code messageClass} — superclasses <em>and</em> interfaces —
     * ordered from the most general type to the concrete one; each type contributes once.
     */
    private static List<MessageProcessor> buildDispatchList(
            Map<Class<?>, List<MessageProcessor>> processors,
            Class<?> messageClass
    ) {
        Logger.trace("Building dispatch list for %s", lazy(() -> Meta.fqn(messageClass)));

        // Breadth-first from the concrete class: superclass and interfaces of each level
        val hierarchy = new LinkedHashSet<Class<?>>();
        val pending = new ArrayDeque<Class<?>>();
        pending.add(messageClass);
        while (!pending.isEmpty()) {
            val klass = pending.poll();
            if (klass == Object.class || !hierarchy.add(klass)) { continue; }
            if (klass.getSuperclass() != null) { pending.add(klass.getSuperclass()); }
            pending.addAll(List.of(klass.getInterfaces()));
        }

        // Process from base to concrete (reversed)
        val types = new ArrayList<>(hierarchy);
        val result = new ArrayList<MessageProcessor>();
        for (var i = types.size() - 1; i >= 0; i--) {
            result.addAll(processors.getOrDefault(types.get(i), EMPTY));
        }

        return result.isEmpty() ? EMPTY : List.copyOf(result);
    }

}
