package br.bundled.tasks.shared;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * Tasks in memory, in a {@link HashMap} by id. A {@code HashMap} is not thread-safe and the server answers requests
 * concurrently, so every method is {@code synchronized} on the store.
 */
@NullMarked
public final class TaskStore {

    private final Map<Integer, Task> tasks = new HashMap<>();
    private int lastId;

    public synchronized Task add(String title) {
        val task = new Task(++lastId, title, false);
        tasks.put(task.id(), task);
        return task;
    }

    /** All tasks, in creation order (a {@code HashMap} has none of its own). */
    public synchronized List<Task> all() {
        return tasks.values().stream().sorted(Comparator.comparingInt(Task::id)).toList();
    }

    public synchronized Optional<Task> markDone(int id, boolean done) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (key, task) -> task.withDone(done)));
    }

    /** Marks an open task done, or a done one open. */
    public synchronized Optional<Task> toggle(int id) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (key, task) -> task.withDone(!task.done())));
    }

    /** Whether there was a task with the id. */
    public synchronized boolean remove(int id) {
        return tasks.remove(id) != null;
    }
}
