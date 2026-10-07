package br.commons.lang.html.core;

import org.jspecify.annotations.NullMarked;

/** What an element accepts in its factory: attributes and child nodes, in any order. */
@NullMarked
public sealed interface Content permits Attribute, Node {}
