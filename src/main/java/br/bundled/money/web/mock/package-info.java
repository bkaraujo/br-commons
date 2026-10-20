/**
 * Stand-in for {@code business._1_application} while it does not exist: an in-memory book ({@link Book}) with a
 * fictitious seed ({@link Seed}). The only place with fake data; the screens use it as they will use the
 * application layer, so replacing it is a change of dependency. Follows the domain rules (sign by account nature,
 * balanced transactions) with the real {@code AccountType}.
 */
@NullMarked
package br.bundled.money.web.mock;

import org.jspecify.annotations.NullMarked;
