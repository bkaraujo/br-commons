package br.commons.persistence.jdbc.migration;

import org.jspecify.annotations.NullMarked;

/** One {@code V<version>__<description>.sql} file: its SQL and the SHA-256 that guards it against edits. */
@NullMarked
public record Migration(int version, String description, String sql, String checksum) {}
