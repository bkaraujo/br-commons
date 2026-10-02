/**
 * Convenience features over the pure HTTP layer of {@link br.commons.http}: route registration in
 * {@link br.commons.web.router}, server built around the router with JSON request and response
 * in {@link br.commons.web.server}, per-request state in {@link br.commons.web.session} and ready-made filters in
 * {@link br.commons.web.filter}. This root package holds the route handler
 * ({@link RouteHandler}) and the uniform error body ({@link ProblemDetail}).
 *
 * <p>Does not know product session, active context or product-specific filters — whoever starts the
 * server builds the chain and decides propagation per request.
 */
@NullMarked
package br.commons.web;

import org.jspecify.annotations.NullMarked;
