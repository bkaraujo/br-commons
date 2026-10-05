/**
 * JDBC access with a built-in connection pool, transactions and migrations.
 *
 * <h2>Main components</h2>
 * <ul>
 *   <li>{@link br.commons.persistence.jdbc.JDBCProperties} — data source configuration (url, credentials, pool sizes,
 *       timeouts).</li>
 *   <li>{@link br.commons.persistence.jdbc.DataSource} — pooled data source: {@code query}/{@code execute}, explicit
 *       transactions ({@code begin}) and {@link br.commons.persistence.jdbc.Transactions#inTransaction}.</li>
 *   <li>{@link br.commons.persistence.jdbc.JDBCRepository} — CRUD generated from table introspection.</li>
 *   <li>{@link br.commons.persistence.jdbc.migration.Migrator} — versioned SQL migrations under a lock.</li>
 *   <li>{@link br.commons.persistence.jdbc.pool.ConnectionPool} — the pool behind {@code DataSource}.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * val props = new JDBCProperties();
 * props.url("jdbc:postgresql://localhost:5432/mydb");
 * props.username("app");
 * props.password(secret);
 * props.maxPoolSize(20);
 *
 * val dataSource = new DataSource(props);
 *
 * // Work that commits on Success and rolls back on Failure; afterCommit runs only after the commit.
 * Result<Order, BusinessError> saved = dataSource.inTransaction(() -> {
 *     dataSource.execute("INSERT INTO orders (id, total) VALUES (?, ?)", JDBCParameter.of(id, total));
 *     dataSource.afterCommit(() -> bus.publish(new OrderPlaced(id)));
 *     return Result.success(order);
 * });
 *
 * new Migrator(() -> DriverManager.getConnection(url, user, password), "db/migration").migrate();
 *
 * dataSource.close();
 * }</pre>
 */
@NullMarked
package br.commons.persistence.jdbc;

import org.jspecify.annotations.NullMarked;
