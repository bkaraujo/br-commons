package br.commons.lang.html.form;

/** What a submitted value of an {@link InputType} is, which decides the constraints it takes. */
public enum InputKind {

    /** Free text: {@code minlength}, {@code maxlength} and {@code pattern}. */
    TEXTUAL,

    /** Ordered values (numbers, dates, times): {@code min} and {@code max}. */
    ORDERED,

    /** Neither: choices, files, buttons, colors. */
    OTHER
}
