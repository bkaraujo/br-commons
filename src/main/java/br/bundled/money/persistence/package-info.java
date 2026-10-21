/**
 * The repositories of the domain ({@code business._0_domain.repository}) over a {@link br.commons.persistence.Storage}
 * of JSON fields: {@code InMemoryStorage} while the book lives only in memory, {@code LocalFileStorage} on disk, with
 * no change here. Each repository holds what it loaded ({@link br.bundled.money.persistence.PersistenceModule} loads
 * at startup) and writes through: reads never touch the storage. Depends on {@code _0_domain} and
 * {@code br.commons.persistence}/{@code br.commons.lang} only.
 *
 * <p>Layout: {@code accounts.json} (one field per account id), {@code transactions-<yyyy-MM>.json} (the transactions
 * of the month, by id), {@code balances-<account id>.json} (the month-end balances kept, by {@code yyyy-MM}),
 * {@code book.json} (field {@code book}).
 */
@NullMarked
package br.bundled.money.persistence;

import org.jspecify.annotations.NullMarked;
