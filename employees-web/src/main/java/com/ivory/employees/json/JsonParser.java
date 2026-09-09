package com.ivory.employees.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small recursive-descent JSON reader, used for request bodies. */
final class JsonParser {

    private final String text;
    private int pos;

    JsonParser(String text) {
        this.text = text == null ? "" : text;
    }

    Object parseDocument() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new JsonException("empty JSON document");
        }
        Object value = parseValue();
        skipWhitespace();
        if (pos < text.length()) {
            throw new JsonException("unexpected trailing content at position " + pos);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new JsonException("unexpected end of JSON document");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            object.put(key, parseValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}' at position " + (pos - 1));
            }
        }
    }

    private List<Object> parseArray() {
        List<Object> values = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return values;
        }
        while (true) {
            values.add(parseValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return values;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']' at position " + (pos - 1));
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw new JsonException("truncated unicode escape at position " + pos);
                    }
                    value.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw new JsonException("invalid escape '\\" + escaped + "' at position " + (pos - 1));
            }
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw new JsonException("invalid literal at position " + pos);
        }
        pos += literal.length();
        return value;
    }

    private BigDecimal parseNumber() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return new BigDecimal(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new JsonException("invalid number at position " + start);
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new JsonException("unexpected end of JSON document");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new JsonException("expected '" + expected + "' but found '" + c + "' at position " + (pos - 1));
        }
    }
}
