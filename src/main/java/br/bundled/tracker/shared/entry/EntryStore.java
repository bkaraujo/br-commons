package br.bundled.tracker.shared.entry;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Entries in memory; safe for the server's concurrent requests. */
@NullMarked
public final class EntryStore {

    private static final Comparator<Entry> CHRONOLOGICAL =
            Comparator.comparing(Entry::date).thenComparing(Entry::start).thenComparing(Entry::id);

    private final Map<Integer, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    public Entry add(EntryInput input) {
        val entry = Entry.of(ids.incrementAndGet(), input);
        entries.put(entry.id(), entry);
        return entry;
    }

    public Optional<Entry> get(int id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Optional<Entry> update(int id, EntryInput input) {
        return Optional.ofNullable(entries.computeIfPresent(id, (_, _) -> Entry.of(id, input)));
    }

    public Optional<Entry> remove(int id) {
        return Optional.ofNullable(entries.remove(id));
    }

    /** The entries that pass the filter (a week, a person…), by day and start. */
    public List<Entry> matching(Predicate<Entry> filter) {
        return entries.values().stream().filter(filter).sorted(CHRONOLOGICAL).toList();
    }

    /** How many entries are on the project: one in use cannot be deleted. */
    public long ofProject(int projectId) {
        return entries.values().stream().filter(entry -> entry.projectId() == projectId).count();
    }

    /** How many entries are of the person: one in use cannot be deleted. */
    public long ofPerson(int personId) {
        return entries.values().stream().filter(entry -> entry.personId() == personId).count();
    }
}
