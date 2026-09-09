package com.ivory.employees.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader for the supplied ASCII data files: semicolon-delimited records whose first record holds
 * the field names.
 *
 * <p>Field names are normalised to upper case and trimmed; {@link Row#get} accepts alternative
 * spellings so the files can use either the correct name or the one printed in the specification
 * (ADRESS_CITY, EMPLYEE_CODE).
 */
public final class DatFile {

    public static final char DELIMITER = ';';

    private DatFile() {
    }

    /** One data record, addressable by field name. */
    public record Row(int lineNumber, Map<String, String> values) {

        /** Returns the first of {@code names} that is present and non-blank, else {@code null}. */
        public String get(String... names) {
            for (String name : names) {
                String value = values.get(name.toUpperCase());
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
        }

        /** Like {@link #get} but fails when no value is present. */
        public String require(String... names) {
            String value = get(names);
            if (value == null) {
                throw new IllegalArgumentException(
                        "line " + lineNumber + ": missing required field " + String.join("/", names));
            }
            return value;
        }
    }

    public static List<Row> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static List<Row> read(Reader source) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(source)) {
            List<String> header = null;
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    line = stripByteOrderMark(line);
                }
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = split(line);
                if (header == null) {
                    header = fields.stream().map(field -> field.trim().toUpperCase()).toList();
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    values.put(header.get(i), i < fields.size() ? fields.get(i).trim() : "");
                }
                rows.add(new Row(lineNumber, values));
            }
            if (header == null) {
                throw new IOException("the file holds no header record");
            }
        }
        return rows;
    }

    private static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == DELIMITER) {
                fields.add(line.substring(start, i));
                start = i + 1;
            }
        }
        fields.add(line.substring(start));
        return fields;
    }

    private static String stripByteOrderMark(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }
}
