package com.ivory.employees;

import com.ivory.employees.db.DataImporter;
import com.ivory.employees.db.DatFile;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatFileTest {

    @Test
    void readsSemicolonRecordsUsingTheFirstRecordAsFieldNames() throws IOException {
        List<DatFile.Row> rows = DatFile.read(new StringReader("""
                CODE;NAME;IS_ACTIVE;ADDRESS_STREET;ADDRESS_NUMBER;ADDRESS_CITY;ADDRESS_COUNTRY
                1001;Avi Cohen;1;Herzl;12;Tel Aviv;Israel

                1002; Maya Levi ;0;Ben Yehuda;45B;Tel Aviv;Israel
                """));

        assertEquals(2, rows.size(), "blank lines are skipped");
        assertEquals("1001", rows.get(0).get("CODE"));
        assertEquals("Maya Levi", rows.get(1).get("NAME"), "values are trimmed");
        assertEquals("Tel Aviv", rows.get(1).get("ADDRESS_CITY"));
        assertNull(rows.get(0).get("MANAGER"), "unknown fields read as null");
    }

    @Test
    void acceptsTheFieldNameSpellingsUsedInTheSpecification() throws IOException {
        List<DatFile.Row> rows = DatFile.read(new StringReader("""
                EMPLYEE_CODE;MONTH;GROSS;TAX;TOTAL
                1001;01/2025;28000;5000;23000
                """));

        DatFile.Row row = rows.get(0);
        assertEquals("1001", row.get("EMPLOYEE_CODE", "EMPLYEE_CODE"));
        assertEquals(LocalDate.of(2025, 1, 1), DataImporter.parseMonth(row.get("MONTH")));
    }

    @Test
    void tolerantOfShortRecordsAndMissingHeaderFields() throws IOException {
        List<DatFile.Row> rows = DatFile.read(new StringReader("""
                CODE;NAME;IS_ACTIVE;ADDRESS_STREET
                1003;Daniel Mizrahi;1
                """));

        assertEquals("Daniel Mizrahi", rows.get(0).get("NAME"));
        assertNull(rows.get(0).get("ADDRESS_STREET"), "missing trailing fields read as null");
        assertThrows(IllegalArgumentException.class, () -> rows.get(0).require("ADDRESS_STREET"));
    }

    /** The exported files are rarely UTF-8; the reader must work the encoding out per file. */
    @Test
    void readsAFileStoredInALegacyHebrewCodePage(@TempDir Path dir) throws IOException {
        Charset windows1255 = Charset.forName("windows-1255");
        Path file = dir.resolve("Employees.dat");
        Files.write(file, ("CODE;NAME;IS_ACTIVE\n1001;\u05d0\u05d1\u05d9 \u05db\u05d4\u05df;1\n")
                .getBytes(windows1255));

        List<DatFile.Row> rows = DatFile.read(file);

        assertEquals("\u05d0\u05d1\u05d9 \u05db\u05d4\u05df", rows.get(0).get("NAME"),
                "a file that is not valid UTF-8 must fall back, not abort the import");
    }

    @Test
    void readsUtf8WithAndWithoutAByteOrderMark(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("plain.dat");
        Files.writeString(plain, "CODE;NAME\n1001;Ren\u00e9e Fran\u00e7ois\n", StandardCharsets.UTF_8);
        assertEquals("Ren\u00e9e Fran\u00e7ois", DatFile.read(plain).get(0).get("NAME"));

        Path withBom = dir.resolve("bom.dat");
        Files.writeString(withBom, "\ufeffCODE;NAME\n1001;Ren\u00e9e Fran\u00e7ois\n", StandardCharsets.UTF_8);
        List<DatFile.Row> rows = DatFile.read(withBom);
        assertEquals("1001", rows.get(0).get("CODE"), "the byte-order mark must not end up in the first field name");
        assertEquals("Ren\u00e9e Fran\u00e7ois", rows.get(0).get("NAME"));
    }

    @Test
    void anExplicitCharsetOverridesTheGuess(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("latin1.dat");
        Files.write(file, "CODE;NAME\n1001;Ren\u00e9e\n".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals("Ren\u00e9e", DatFile.read(file, StandardCharsets.ISO_8859_1).get(0).get("NAME"));
    }

    @Test
    void failsWhenTheFileHasNoHeader() {
        assertThrows(IOException.class, () -> DatFile.read(new StringReader("")));
    }

    @Test
    void parsesTheScalarFieldTypes() {
        assertEquals(1, DataImporter.parseFlag("1"));
        assertEquals(1, DataImporter.parseFlag("true"));
        assertEquals(0, DataImporter.parseFlag("0"));
        assertEquals(0, DataImporter.parseFlag(null));
        assertThrows(IllegalArgumentException.class, () -> DataImporter.parseFlag("maybe"));

        assertEquals(new BigDecimal("28000.00"), DataImporter.parseAmount(" 28,000 "));
        assertThrows(IllegalArgumentException.class, () -> DataImporter.parseAmount("n/a"));

        assertEquals(LocalDate.of(2025, 3, 1), DataImporter.parseMonth("2025-03-01"));
        assertEquals(LocalDate.of(2025, 3, 1), DataImporter.parseMonth("2025-03"));
        assertEquals(LocalDate.of(2025, 3, 4), DataImporter.parseMonth("04/03/2025"));
        assertThrows(IllegalArgumentException.class, () -> DataImporter.parseMonth("March 2025"));
    }
}
