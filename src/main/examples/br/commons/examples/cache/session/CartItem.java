package br.commons.examples.cache.session;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/** One line of a user's shopping cart: what the session keeps hot. */
@NullMarked
public record CartItem(UUID id, long quantity, long priceCents) {}
