package com.ivory.employees.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reader for the supplied ASCII data files: semicolon-delimited records whose first record holds
 * the field names.
 *
 * <p>Field names are normalised to upper case and trimmed; {@link Row#get} accepts alternative
 * spellings so the files can use either the correct name or the one printed in the specification
 * (ADRESS_CITY, EMPLYEE_CODE).
 *
 * <p>The specification calls these files ASCII, but exported files regularly are not: they carry a
 * byte-order mark, or hold accented or Hebrew text in a legacy code page. The encoding is therefore
 * worked out per file rather than assumed - see {@link #read(Path, Charset)}.
 */
public final class DatFile {

    private static final Logger LOG = Logger.getLogger(DatFile.class.getName());

    /** Used when a file's delimiter cannot be worked out from its header. */
    public static final char DEFAULT_DELIMITER = ';';

    /** Delimiters {@link #detectDelimiter} chooses between, in preference order on a tie. */
    private static final char[] CANDIDATE_DELIMITERS = {';', ',', '\t', '|'};

    /**
     * Used when a file is neither byte-order marked nor valid UTF-8. The specification comes from an
     * Israeli source, so the Hebrew ANSI code page is the likeliest legacy encoding; it is also a
     * superset of ISO-8859-8. Override with {@code employees.data.charset} when it guesses wrong.
     */
    private static final String FALLBACK_CHARSET = "windows-1255";

    private DatFile() {
    }

    /** A parsed file: the field names taken from its first record, plus the data records. */
    public record Table(List<String> header, List<Row> rows, char delimiter) {

        /** True when the header carries any of these field names. */
        public boolean has(String... names) {
            for (String name : names) {
                if (header.contains(name.toUpperCase())) {
                    return true;
                }
            }
            return false;
        }
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

    /** Reads a file, working out its encoding and delimiter. */
    public static Table read(Path file) throws IOException {
        return read(file, null, null);
    }

    /** Reads a file with a known encoding, working out its delimiter. */
    public static Table read(Path file, Charset charset) throws IOException {
        return read(file, charset, null);
    }

    /**
     * Reads a file.
     *
     * @param charset the encoding to use, or {@code null} to work it out: a byte-order mark decides
     *                when there is one, otherwise the file is decoded as UTF-8, and only if that
     *                fails is it re-read as {@value #FALLBACK_CHARSET} (with a warning naming the
     *                file, so a wrong guess is visible rather than silent).
     * @param delimiter the field separator, or {@code null} to work it out from the header record
     */
    public static Table read(Path file, Charset charset, Character delimiter) throws IOException {
        Charset declared = charset != null ? charset : byteOrderMarkCharset(file);
        if (declared != null) {
            try (Reader reader = open(file, declared, CodingErrorAction.REPLACE)) {
                return read(reader, delimiter);
            }
        }
        try (Reader reader = open(file, StandardCharsets.UTF_8, CodingErrorAction.REPORT)) {
            return read(reader, delimiter);
        } catch (CharacterCodingException e) {
            Charset fallback = fallbackCharset();
            LOG.warning(() -> file.getFileName() + " is not valid UTF-8; reading it as " + fallback
                    + ". If the text comes out wrong, set -Demployees.data.charset=<encoding>.");
            try (Reader reader = open(file, fallback, CodingErrorAction.REPLACE)) {
                return read(reader, delimiter);
            }
        }
    }

    private static Reader open(Path file, Charset charset, CodingErrorAction onError) throws IOException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(onError)
                .onUnmappableCharacter(onError);
        return new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder));
    }

    /** Returns the charset a byte-order mark names, or {@code null} when the file carries none. */
    private static Charset byteOrderMarkCharset(Path file) throws IOException {
        byte[] head = new byte[3];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (read >= 2 && head[0] == (byte) 0xFF && head[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (read >= 2 && head[0] == (byte) 0xFE && head[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private static Charset fallbackCharset() {
        // ISO-8859-1 is the last resort: it is always present and maps every byte, so the import
        // gets through even on a JDK image without the extended charsets.
        return Charset.isSupported(FALLBACK_CHARSET) ? Charset.forName(FALLBACK_CHARSET)
                : StandardCharsets.ISO_8859_1;
    }

    public static Table read(Reader source) throws IOException {
        return read(source, null);
    }

    /**
     * Reads records from an open reader.
     *
     * @param delimiter the field separator, or {@code null} to work it out from the header record
     */
    public static Table read(Reader source, Character delimiter) throws IOException {
        List<Row> rows = new ArrayList<>();
        char separator = delimiter == null ? DEFAULT_DELIMITER : delimiter;
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
                if (header == null) {
                    if (delimiter == null) {
                        separator = detectDelimiter(line);
                    }
                    header = split(line, separator).stream()
                            .map(field -> field.trim().toUpperCase())
                            .toList();
                    continue;
                }
                List<String> fields = split(line, separator);
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    values.put(header.get(i), i < fields.size() ? fields.get(i).trim() : "");
                }
                rows.add(new Row(lineNumber, values));
            }
            if (header == null) {
                throw new IOException("the file holds no header record");
            }
            return new Table(header, rows, separator);
        }
    }

    /**
     * Picks the delimiter a header record uses: whichever candidate appears most often in it. The
     * specification describes these files as semicolon-delimited, but real exports use a comma just
     * as often, so the separator is worked out rather than assumed.
     */
    static char detectDelimiter(String headerLine) {
        char chosen = DEFAULT_DELIMITER;
        int best = 0;
        for (char candidate : CANDIDATE_DELIMITERS) {
            int count = 0;
            for (int i = 0; i < headerLine.length(); i++) {
                if (headerLine.charAt(i) == candidate) {
                    count++;
                }
            }
            if (count > best) {
                best = count;
                chosen = candidate;
            }
        }
        return chosen;
    }

    private static List<String> split(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == delimiter) {
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
