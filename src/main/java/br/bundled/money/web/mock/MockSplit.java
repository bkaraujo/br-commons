package br.bundled.money.web.mock;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;

/** Amount signed from the account type point of view: positive increases its balance. */
@NullMarked
public record MockSplit(int account, BigDecimal amount) {}
