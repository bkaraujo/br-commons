package br.commons.lang.html;

import java.util.stream.Collectors;

/**
 * Expected markup written as an indented text block, one tag per line, flattened to what the renderer writes
 * (no whitespace between tags). Keep each tag whole on its line: line edges are dropped, not replaced by spaces.
 */
final class Markup {

    private Markup() {}

    static String flat(String block) {
        return block.lines().map(String::strip).collect(Collectors.joining());
    }
}
