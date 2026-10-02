package br.commons.html.core;

import org.jspecify.annotations.NullMarked;

/** A value of an enumerated attribute, written as the keyword the HTML spec defines. */
@NullMarked
public interface Keyword {

    String token();
}
