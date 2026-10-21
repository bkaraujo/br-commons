/**
 * What the screens read and write, over the use cases ({@code business._1_application}): {@link
 * br.bundled.money.web.book.Book} answers in the domain's types ({@code Account}, {@code Transaction}) what a screen
 * needs at once (the tree, paths, balances of a day, the ledger with its running balance, the latest transactions)
 * and passes the changes to the services. Screen arithmetic only: the rules are in the domain and the services.
 */
@NullMarked
package br.bundled.money.web.book;

import org.jspecify.annotations.NullMarked;
