package br.commons.locale.i18n;

import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;

/** A bundle argument formatted as day and short month ({@link Formats#dayMonth}) instead of the full date. */
@NullMarked
public record DayMonth(LocalDate date) {}
