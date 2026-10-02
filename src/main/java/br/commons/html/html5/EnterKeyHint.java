package br.commons.html.html5;

import br.commons.html.core.Keyword;
import org.jspecify.annotations.NullMarked;

/**
 * Label of the virtual keyboard enter key ({@code enterkeyhint}).
 *
 * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-enterkeyhint">HTML: the enterkeyhint attribute</a>
 */
@NullMarked
public enum EnterKeyHint implements Keyword {

    /** Inserts a new line. */
    ENTER("enter"),

    /** Nothing more to input: closes the input method. */
    DONE("done"),

    /** Takes the person to the target of the text they typed. */
    GO("go"),

    /** Moves to the next field that takes text. */
    NEXT("next"),

    /** Moves to the previous field that takes text. */
    PREVIOUS("previous"),

    /** Searches for the text typed. */
    SEARCH("search"),

    /** Sends the text to its target (a message). */
    SEND("send");

    private final String token;

    EnterKeyHint(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
