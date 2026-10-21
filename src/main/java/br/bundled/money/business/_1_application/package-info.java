/**
 * The use cases of br.money, one method per operation of the user (create an account, post a transaction, close the
 * period...), over the domain's repositories ({@code _0_domain.repository}). Each answers
 * {@code Result<T, BusinessError>}: a rule broken ({@code BusinessException} of the domain) or a storage failure
 * ({@code BusinessError.Infrastructure}) become the failure. The book is changed by one use case at a time (a lock
 * shared by the services, see {@link br.bundled.money.business._1_application.ApplicationModule}). No HTTP, HTML nor
 * storage format here. Depends on {@code _0_domain} and {@code br.commons} only.
 */
@NullMarked
package br.bundled.money.business._1_application;

import org.jspecify.annotations.NullMarked;
