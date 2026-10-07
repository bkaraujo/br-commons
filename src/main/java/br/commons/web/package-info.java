/**
 * Convenience features over the pure HTTP layer of {@link br.commons.protocol.http}: route registration in
 * {@link br.commons.web.router}, server built around the router with JSON request in {@link br.commons.web.server}
 * and ready-made filters in {@link br.commons.web.filter}. This root package holds the vocabulary shared by all of
 * them: the route handler ({@link RouteHandler}), the response factories ({@link WebResponse}), the uniform error
 * body ({@link ProblemDetail}) and the error page contract ({@link ErrorPage}).
 *
 * <p>Does not know product session, active context or product-specific filters — whoever starts the
 * server builds the chain and decides propagation per request.
 */
@NullMarked
package br.commons.web;

import org.jspecify.annotations.NullMarked;
