package br.bundled.money.web.mock;

import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;
import java.util.List;

@NullMarked
public record MockTransaction(int id, LocalDate date, String description, List<MockSplit> splits) {}
