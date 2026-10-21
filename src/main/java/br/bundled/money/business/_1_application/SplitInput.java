package br.bundled.money.business._1_application;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.UUID;

/** A split to post: the account and the amount in its nature (INTENT §6: positive increases its balance). */
@NullMarked
public record SplitInput(UUID account, BigDecimal amount) {}
