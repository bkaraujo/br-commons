package br.commons.examples.tracker.shared.entry;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/** A block of time a person worked on a project, within one day ({@code end} after {@code start}). */
@NullMarked
public record Entry(int id, LocalDate date, int projectId, int personId, String description, LocalTime start,
             LocalTime end) {

    public static Entry of(int id, EntryInput input) {
        return new Entry(id, input.date(), input.projectId(), input.personId(), input.description(), input.start(),
                input.end());
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
