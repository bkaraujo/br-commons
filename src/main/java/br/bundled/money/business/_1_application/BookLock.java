package br.bundled.money.business._1_application;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.locks.ReentrantLock;

/** The lock the use cases share, its own key in the {@link br.commons.cdi.Context}: the book is changed by one use case at a time. */
@NullMarked
public final class BookLock extends ReentrantLock {}
