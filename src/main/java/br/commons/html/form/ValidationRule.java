package br.commons.html.form;


/** The constraint of a {@link Field} that a submitted value broke. */
public enum ValidationRule {
    REQUIRED, INVALID, TOO_SHORT, TOO_LONG, PATTERN, BELOW_MIN, ABOVE_MAX, STEP
}
