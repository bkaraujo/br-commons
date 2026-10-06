package br.commons.examples.tracker.shared.registry;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/** A register in memory (projects, people), listed by name; safe for the server's concurrent requests. */
@NullMarked
public final class Registry<T extends Registered> {

    private final Map<Integer, T> items = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    /** Adds what {@code create} builds with the new id. */
    public T add(IntFunction<T> create) {
        val item = create.apply(ids.incrementAndGet());
        items.put(item.id(), item);
        return item;
    }

    public Optional<T> get(int id) {
        return Optional.ofNullable(items.get(id));
    }

    public Optional<T> update(int id, UnaryOperator<T> change) {
        return Optional.ofNullable(items.computeIfPresent(id, (key, item) -> change.apply(item)));
    }

    public Optional<T> remove(int id) {
        return Optional.ofNullable(items.remove(id));
    }

    public List<T> all() {
        return items.values().stream().sorted(Comparator.comparing(item -> item.name().toLowerCase(Locale.ROOT))).toList();
    }

    /** What can still be chosen in a new entry. */
    public List<T> active() {
        return all().stream().filter(Registered::active).toList();
    }

    public String nameOf(int id) {
        return get(id).map(Registered::name).orElse("—");
    }
}
