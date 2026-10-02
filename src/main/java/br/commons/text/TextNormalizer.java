package br.commons.text;

import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@NullMarked
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {}

    public static String normalize(String text) {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        val withoutMarks = MARKS.matcher(decomposed).replaceAll("");
        return SPACES.matcher(withoutMarks.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
