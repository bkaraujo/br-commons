package br.bundled.tracker.shared.entry;

import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;
import java.time.LocalTime;

/** The values of an entry, already checked (the week screen reads them from its form). */
@NullMarked
public record EntryInput(LocalDate date, int projectId, int personId, String description, LocalTime start, LocalTime end) {}
