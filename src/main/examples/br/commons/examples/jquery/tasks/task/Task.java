package br.commons.examples.jquery.tasks.task;

import org.jspecify.annotations.NullMarked;

/** A task of the list; immutable, replaced when marked done or open. Sent to the browser as JSON. */
@NullMarked
public record Task(int id, String title, boolean done) {

    public Task withDone(boolean done) {
        return new Task(id, title, done);
    }
}
