package br.commons.html.form;

import br.commons.i18n.Text;
import org.jspecify.annotations.NullMarked;

/** An option of a {@code <select>} built by {@code Html}: the submitted value and the text shown. */
@NullMarked
public record Option(String value, Text label) {}
