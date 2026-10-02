package br.commons.serializer.json;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recursive-descent minimalista. Sem streaming, sem validação exaustiva — payloads internos, curtos. */
@NullMarked
final class JsonParser {

    private final String source;
    private int pos;

    JsonParser(String source) {
        this.source = source;
        this.pos = 0;
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    @Nullable Object parseValue() {
        skipWhitespace();
        if (pos >= source.length()) return null;
        val c = source.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        val map = new LinkedHashMap<String, Object>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            val key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            val next = source.charAt(pos++);
            if (next == '}') break;
            if (next != ',') throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
        }
        return map;
    }

    private List<Object> parseArray() {
        val list = new ArrayList<Object>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            val next = source.charAt(pos++);
            if (next == ']') break;
            if (next != ',') throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
        }
        return list;
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private String parseString() {
        expect('"');
        val sb = new StringBuilder();
        while (true) {
            val c = source.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                val esc = source.charAt(pos++);
                sb.append(switch (esc) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'u' -> {
                        val unicode = (char) Integer.parseInt(source.substring(pos, pos + 4), 16);
                        pos += 4;
                        yield unicode;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape: \\" + esc);
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean parseBoolean() {
        if (source.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (source.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new IllegalArgumentException("Invalid literal at " + pos);
    }

    private @Nullable Object parseNull() {
        if (source.startsWith("null", pos)) { pos += 4; return null; }
        throw new IllegalArgumentException("Invalid literal at " + pos);
    }

    private Double parseNumber() {
        val start = pos;
        while (pos < source.length() && "-+.eE0123456789".indexOf(source.charAt(pos)) >= 0) pos++;
        return Double.parseDouble(source.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
    }

    private char peek() {
        return source.charAt(pos);
    }

    private void expect(char expected) {
        skipWhitespace();
        val actual = source.charAt(pos++);
        if (actual != expected) throw new IllegalArgumentException("Expected '" + expected + "' at " + pos);
    }
}
