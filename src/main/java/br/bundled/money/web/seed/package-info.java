/**
 * The data a development run starts with, loaded through the use cases ({@code ImportService}): the book of a GnuCash
 * file named by {@code br.commons.money.seed.gnucash} (read at startup, real data never enters the repository), or a
 * fictitious one when the book is empty. The only place with fake data.
 */
@NullMarked
package br.bundled.money.web.seed;

import org.jspecify.annotations.NullMarked;
