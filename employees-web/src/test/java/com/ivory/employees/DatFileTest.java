package com.ivory.employees;

import com.ivory.employees.db.DataImporter;
import com.ivory.employees.db.DatFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
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
