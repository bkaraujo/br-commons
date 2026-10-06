/**
 * Example: a {@link br.commons.cache.CacheServer} region used as a per-user-session cache. The generic cache knows only
 * partitions; here the partition is the user id, {@code login} loads it and {@code logout} unloads it. Start at
 * {@link br.commons.examples.cache.session.SessionCacheApp}.
 *
 * <p>Classes: {@code CartItem} — the cached model; {@code CartRegion} — its binary layout; {@code UserSessions} —
 * maps login/logout onto {@code load}/{@code unload}; {@code SessionCacheApp} — runs two sessions side by side.
 */
@NullMarked
package br.commons.examples.cache.session;

import org.jspecify.annotations.NullMarked;
