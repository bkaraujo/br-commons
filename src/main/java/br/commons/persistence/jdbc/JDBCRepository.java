package br.commons.persistence.jdbc;

import br.commons.Logger;
import br.commons.Result;
import br.commons.cdi.Context;
import br.commons.persistence.jdbc.primitives.JDBCMetaData;
import br.commons.persistence.jdbc.primitives.JDBCParameter;
import br.commons.persistence.jdbc.primitives.JDBCResultSet;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base JDBC repository for a single table. The constructor queries the
 * {@link JDBCMetaData} (DatabaseMetaData) of the connection to discover the columns and
 * the primary key of the table, and provides generic CRUD operations
 * ({@link #findAll()}, {@link #findById(Object...)}, {@link #save(Object)},
 * {@link #deleteById(Object...)}, {@link #count()}, {@link #exists(Object...)}).
 *
 * <p>Subtypes implement only the mapping between row and entity:
 * {@link #map(JDBCResultSet)} and {@link #values(Object)}. Columns that should not be
 * overwritten in {@code UPDATE} (e.g.: {@code TMS_CREATE_AT}) are declared in
 * {@link #updateImmutableColumns()}.
 *
 * @param <T> type of the entity persisted in this table
 */
@NullMarked
public abstract class JDBCRepository<T> {

    protected final DataSource dataSource = Context.get(DataSource.class);

    private final @Nullable String schema;
    public @Nullable String schema() { return schema; }

    private final String table;
    public String table() { return table; }

    private final List<String> columns = new ArrayList<>();
    public List<String> columns() { return columns; }

    private final List<String> primaryKeys = new ArrayList<>();
    public List<String> primaryKeys() { return primaryKeys; }

    protected JDBCRepository(String table) {
        this(null, table);
    }

    protected JDBCRepository(@Nullable String schema, String table) {
        this.schema = schema;
        this.table = table;
        introspect();
    }

    // ----------------------------------------------------------------------
    // Subtype hooks
    // ----------------------------------------------------------------------

    /** Maps the current row of the ResultSet to the entity. */
    protected abstract T map(JDBCResultSet rs);

    /** Entity values per column (already converted: UUID#toString, enum#name, "Y"/"N", Timestamp/Date). */
    protected abstract Map<String, @Nullable Object> values(T entity);

    /** Columns preserved in UPDATE (e.g.: {@code Set.of("TMS_CREATE_AT")}). Empty by default. */
    protected Set<String> updateImmutableColumns() {
        return Set.of();
    }

    // ----------------------------------------------------------------------
    // Generic CRUD
    // ----------------------------------------------------------------------

    public List<T> findAll() {
        return dataSource.query(
                "SELECT " + columnList() + " FROM " + table,
                this::mapList
        );
    }

    public Optional<T> findById(Object... pk) {
        return dataSource.query(
                "SELECT " + columnList() + " FROM " + table + " WHERE " + pkWhere(),
                JDBCParameter.of(pk),
                this::mapList
        ).stream().findFirst();
    }

    public boolean exists(Object... pk) {
        return dataSource.query(
                "SELECT 1 FROM " + table + " WHERE " + pkWhere(),
                JDBCParameter.of(pk),
                rs -> rs.next().get()
        );
    }

    public long count() {
        return dataSource.query(
                "SELECT COUNT(*) FROM " + table,
                rs -> { rs.next().get(); return rs.getLong(1).get(); }
        );
    }

    public T insert(T entity) {
        val vals = values(entity);
        dataSource.execute(insertSql(), insertParams(vals));
        return entity;
    }

    public T update(T entity) {
        val vals = values(entity);
        dataSource.execute(updateSql(), updateParams(vals));
        return entity;
    }

    /** Inserts or updates the entity atomically (checks existence and saves in the same transaction). */
    public T save(T entity) {
        val vals = values(entity);
        val pk = pkValues(vals);

        dataSource.transaction(tx -> {
            val exists = tx.query(
                    "SELECT 1 FROM " + table + " WHERE " + pkWhere(),
                    JDBCParameter.of(pk),
                    rs -> rs.next().get()
            ).get();

            if (exists) tx.execute(updateSql(), updateParams(vals)).get();
            else tx.execute(insertSql(), insertParams(vals)).get();

            return Result.success(true);
        });
        return entity;
    }

    public void deleteById(Object... pk) {
        dataSource.execute(
                "DELETE FROM " + table + " WHERE " + pkWhere(),
                JDBCParameter.of(pk)
        );
    }

    // ----------------------------------------------------------------------
    // SQL + parâmetros
    // ----------------------------------------------------------------------

    protected String columnList() {
        return String.join(", ", columns);
    }

    private String pkWhere() {
        return primaryKeys.stream().map(c -> c + " = ?").collect(Collectors.joining(" AND "));
    }

    private List<String> updatableColumns() {
        val immutable = updateImmutableColumns();
        return columns.stream()
                .filter(c -> !primaryKeys.contains(c))
                .filter(c -> !immutable.contains(c))
                .toList();
    }

    private String insertSql() {
        val placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + columnList() + ") VALUES (" + placeholders + ")";
    }

    private String updateSql() {
        val set = updatableColumns().stream().map(c -> c + " = ?").collect(Collectors.joining(", "));
        return "UPDATE " + table + " SET " + set + " WHERE " + pkWhere();
    }

    private List<JDBCParameter> insertParams(Map<String, @Nullable Object> vals) {
        return JDBCParameter.of(columns.stream().map(vals::get).toArray());
    }

    private List<JDBCParameter> updateParams(Map<String, @Nullable Object> vals) {
        val ordered = new ArrayList<@Nullable Object>();
        updatableColumns().forEach(c -> ordered.add(vals.get(c)));
        primaryKeys.forEach(c -> ordered.add(vals.get(c)));
        return JDBCParameter.of(ordered.toArray());
    }

    private Object[] pkValues(Map<String, @Nullable Object> vals) {
        return primaryKeys.stream().map(vals::get).toArray();
    }

    protected List<T> mapList(JDBCResultSet rs) {
        val list = new ArrayList<T>();
        while (rs.next().get()) list.add(map(rs));
        return list;
    }

    // ----------------------------------------------------------------------
    // Metadata introspection
    // ----------------------------------------------------------------------

    private void introspect() {
        val schemaPattern = (schema == null || schema.isBlank()) ? null : schema;

        dataSource.begin()
                .flatMap(tx -> tx.execute(conn -> conn.getMetaData().map(md -> readMetaData(md, schemaPattern))))
                .ifFailure(error -> Logger.fatal("Failed to introspect table %s: %s", table, error))
                .get();

        if (columns.isEmpty()) {
            Logger.fatal("Table %s has no columns (not found in metadata)", table);
        }
    }

    private Boolean readMetaData(JDBCMetaData md, @Nullable String schemaPattern) {
        val columnsRs = md.getColumns(null, schemaPattern, table, null).get();
        while (columnsRs.next().get()) {
            columns.add(columnsRs.getString("COLUMN_NAME").get());
        }

        val pkBySeq = new TreeMap<Integer, String>();
        val pkRs = md.getPrimaryKeys(null, schemaPattern, table).get();
        while (pkRs.next().get()) {
            pkBySeq.put(pkRs.getInt("KEY_SEQ").get(), pkRs.getString("COLUMN_NAME").get());
        }
        primaryKeys.addAll(pkBySeq.values());

        return true;
    }
}
