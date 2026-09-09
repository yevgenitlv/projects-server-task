package com.ivory.employees.json;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free JSON support: serialization of the plain structures the API builds
 * ({@link Map}, {@link Collection}, {@link String}, {@link Number}, {@link Boolean}, {@code null},
 * {@link Temporal}) and parsing of the small request bodies the API accepts.
 *
 * <p>Output uses {@link LinkedHashMap} ordering, so fields appear in the order the service put
 * them in, and is pretty-printed by default (requirement: "clear and well structured JSON").
 */
public final class Json {

    private Json() {
    }

    /** Convenience factory for an ordered JSON object under construction. */
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    public static String write(Object value) {
        return write(value, true);
    }

    public static String write(Object value, boolean pretty) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value, pretty, 0);
        return out.toString();
    }

    /** Parses a JSON document into Map / List / String / BigDecimal / Boolean / null. */
    public static Object parse(String text) {
        return new JsonParser(text).parseDocument();
    }

    /** Parses a JSON document that must be an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object parsed = parse(text);
        if (!(parsed instanceof Map)) {
            throw new JsonException("expected a JSON object but got "
                    + (parsed == null ? "null" : parsed.getClass().getSimpleName()));
        }
        return (Map<String, Object>) parsed;
    }

    private static void writeValue(StringBuilder out, Object value, boolean pretty, int depth) {
        switch (value) {
            case null -> out.append("null");
            case String s -> writeString(out, s);
            case Boolean b -> out.append(b.toString());
            case BigDecimal d -> out.append(d.toPlainString());
            case Number n -> out.append(n.toString());
            case Map<?, ?> map -> writeObject(out, map, pretty, depth);
            case Collection<?> collection -> writeArray(out, collection, pretty, depth);
            case Object[] array -> writeArray(out, java.util.Arrays.asList(array), pretty, depth);
            case Temporal t -> writeString(out, t.toString());
            case Enum<?> e -> writeString(out, e.name());
            default -> writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newLine(out, pretty, depth + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(out, entry.getValue(), pretty, depth + 1);
        }
        newLine(out, pretty, depth);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> values, boolean pretty, int depth) {
        if (values.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newLine(out, pretty, depth + 1);
            writeValue(out, value, pretty, depth + 1);
        }
        newLine(out, pretty, depth);
        out.append(']');
    }

    private static void newLine(StringBuilder out, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        out.append("  ".repeat(depth));
    }

    static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
