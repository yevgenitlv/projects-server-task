package com.ivory.employees.db;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.util.HebrewText;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Imports Employees.dat and Salaries.dat into the EMPLOYEES and SALARIES tables. */
public final class DataImporter {

    private static final Logger LOG = Logger.getLogger(DataImporter.class.getName());

    private static final String EMPLOYEES_FILE = "Employees.dat";
    private static final String SALARIES_FILE = "Salaries.dat";

    /** Date layouts accepted for SALARIES.MONTH, tried in order. */
    private static final List<DateTimeFormatter> DAY_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    private static final List<DateTimeFormatter> MONTH_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM"),
            DateTimeFormatter.ofPattern("MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMM"));

    private final Database database;
    private final AppConfig config;

    public DataImporter(Database database, AppConfig config) {
        this.database = database;
        this.config = config;
    }

    /**
     * Loads the .dat files when the tables are empty, or when {@code employees.data.reload=true}.
     * Does nothing (with a log line) when there is nothing to do.
     */
    public void importIfNeeded() {
        Path dataDir = config.dataDir().toAbsolutePath().normalize();
        try {
            boolean empty = countRows("EMPLOYEES") == 0;
            if (!empty && !config.reloadData()) {
                LOG.info(() -> "Skipping import: EMPLOYEES already holds " + countRows("EMPLOYEES")
                        + " rows (set employees.data.reload=true to re-import from " + dataDir + ")");
                return;
            }
            Path employeesFile = dataDir.resolve(EMPLOYEES_FILE);
            Path salariesFile = dataDir.resolve(SALARIES_FILE);
            if (!Files.isReadable(employeesFile)) {
                LOG.warning(() -> "No " + EMPLOYEES_FILE + " found in " + dataDir + " - starting with an empty database");
                return;
            }
            if (config.reloadData()) {
                deleteAll();
            }
            int employees = importEmployees(employeesFile);
            int salaries = Files.isReadable(salariesFile)
                    ? importSalaries(salariesFile, employeeCodes())
                    : 0;
            if (!Files.isReadable(salariesFile)) {
                LOG.warning(() -> "No " + SALARIES_FILE + " found in " + dataDir + " - salaries left empty");
            }
            LOG.info("Imported " + employees + " employees and " + salaries + " salary rows from " + dataDir);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to import the data files from " + dataDir, e);
        }
    }

    /** Index of EMPLOYEES.NAME, the one column whose value may itself contain the delimiter. */
    private static final int EMPLOYEE_NAME_COLUMN = 1;

    private int importEmployees(Path file) throws IOException, SQLException {
        DatFile.Table table = DatFile.read(file, config.dataCharset(), config.dataDelimiter(),
                EMPLOYEE_NAME_COLUMN);
        describe(file, table);
        requireFields(file, table, List.of(new String[]{"CODE"}, new String[]{"NAME"}));
        Skipped skipped = new Skipped(file);
        String sql = """
                MERGE INTO EMPLOYEES (CODE, NAME, IS_ACTIVE, ADDRESS_STREET, ADDRESS_NUMBER,
                                      ADDRESS_CITY, ADDRESS_COUNTRY)
                KEY (CODE) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int batched = 0;
            for (DatFile.Row row : table.rows()) {
                try {
                    statement.setLong(1, Long.parseLong(row.require("CODE")));
                    String name = row.get("NAME");
                    statement.setString(2, text(name == null ? "" : name));
                    statement.setInt(3, parseFlag(row.get("IS_ACTIVE")));
                    statement.setString(4, text(row.get("ADDRESS_STREET", "ADRESS_STREET")));
                    statement.setString(5, text(row.get("ADDRESS_NUMBER", "ADRESS_NUMBER")));
                    statement.setString(6, text(row.get("ADDRESS_CITY", "ADRESS_CITY")));
                    statement.setString(7, text(row.get("ADDRESS_COUNTRY", "ADRESS_COUNTRY")));
                    statement.addBatch();
                    batched++;
                } catch (RuntimeException e) {
                    skipped.record(row.lineNumber(), e);
                }
            }
            statement.executeBatch();
            skipped.summarise();
            return batched;
        }
    }

    private int importSalaries(Path file, Set<Long> knownEmployees) throws IOException, SQLException {
        DatFile.Table table = DatFile.read(file, config.dataCharset(), config.dataDelimiter());
        describe(file, table);
        requireFields(file, table, List.of(
                new String[]{"EMPLOYEE_CODE", "EMPLYEE_CODE", "CODE"},
                new String[]{"MONTH"},
                new String[]{"GROSS"},
                new String[]{"TAX"}));
        Skipped skipped = new Skipped(file);
        String sql = """
                MERGE INTO SALARIES (EMPLOYEE_CODE, "MONTH", GROSS, TAX, TOTAL)
                KEY (EMPLOYEE_CODE, "MONTH") VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int batched = 0;
            for (DatFile.Row row : table.rows()) {
                try {
                    BigDecimal gross = parseAmount(row.require("GROSS"));
                    BigDecimal tax = parseAmount(row.require("TAX"));
                    String rawTotal = row.get("TOTAL");
                    BigDecimal total = rawTotal == null ? gross.subtract(tax) : parseAmount(rawTotal);

                    long employee = Long.parseLong(row.require("EMPLOYEE_CODE", "EMPLYEE_CODE", "CODE"));
                    if (!knownEmployees.contains(employee)) {
                        // A salary for an employee the other file does not list. Keeping the row
                        // would break the foreign key and abort the whole import.
                        throw new IllegalArgumentException("no employee with code " + employee);
                    }
                    statement.setLong(1, employee);
                    statement.setDate(2, Date.valueOf(parseMonth(row.require("MONTH"))));
                    statement.setBigDecimal(3, gross);
                    statement.setBigDecimal(4, tax);
                    statement.setBigDecimal(5, total);
                    statement.addBatch();
                    batched++;
                } catch (RuntimeException e) {
                    skipped.record(row.lineNumber(), e);
                }
            }
            statement.executeBatch();
            skipped.summarise();
            return batched;
        }
    }

    /**
     * Applies the Hebrew direction conversion when the files are visual-order; otherwise stores the
     * value exactly as the file has it.
     */
    private String text(String value) {
        return config.hebrewIsVisual() ? HebrewText.toLogical(value) : value;
    }

    /** The employee codes now in the database, used to reject salaries that reference no employee. */
    private Set<Long> employeeCodes() throws SQLException {
        try (Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT CODE FROM EMPLOYEES")) {
            Set<Long> codes = new HashSet<>();
            while (resultSet.next()) {
                codes.add(resultSet.getLong(1));
            }
            return codes;
        }
    }

    /** Logs the field names a file declares, so a header mismatch is visible at a glance. */
    private static void describe(Path file, DatFile.Table table) {
        LOG.info(() -> file.getFileName() + ": " + table.rows().size() + " record(s), delimiter '"
                + (table.delimiter() == '\t' ? "\\t" : String.valueOf(table.delimiter()))
                + "', fields " + table.header());
    }

    /**
     * Fails immediately when the header lacks a field the import needs. Without this a wrong header
     * produces one warning per record - thousands of them - and an empty table at the end.
     *
     * @param required each entry is a set of accepted spellings for one field
     */
    private static void requireFields(Path file, DatFile.Table table, List<String[]> required) {
        List<String> missing = required.stream()
                .filter(names -> !table.has(names))
                .map(names -> String.join("/", names))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(file.getFileName() + " has no column for " + missing
                    + ". Its header declares " + table.header()
                    + ". Rename the columns, or adjust the field names this importer accepts.");
        }
    }

    /** Counts unreadable records, logging the first few in full and the rest as a total. */
    private static final class Skipped {

        private static final int LOGGED_IN_FULL = 10;

        private final Path file;
        private int count;

        private Skipped(Path file) {
            this.file = file;
        }

        void record(int lineNumber, RuntimeException failure) {
            count++;
            if (count <= LOGGED_IN_FULL) {
                LOG.warning(() -> "Skipping " + file.getFileName() + " line " + lineNumber + ": "
                        + failure.getMessage());
            } else if (count == LOGGED_IN_FULL + 1) {
                LOG.warning(() -> "Further skipped records in " + file.getFileName()
                        + " are counted but not logged individually");
            }
        }

        void summarise() {
            if (count > 0) {
                LOG.warning(() -> "Skipped " + count + " record(s) in " + file.getFileName());
            }
        }
    }

    private void deleteAll() throws SQLException {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM SALARIES");
            statement.executeUpdate("DELETE FROM EMPLOYEES");
            LOG.info("Cleared EMPLOYEES and SALARIES before re-import");
        }
    }

    private long countRows(String table) {
        try (Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count rows of " + table, e);
        }
    }

    /** IS_ACTIVE is numeric(1); the files may also spell it as true/false/Y/N. */
    public static int parseFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String value = raw.trim();
        return switch (value.toUpperCase()) {
            case "1", "Y", "YES", "TRUE", "T", "A", "ACTIVE" -> 1;
            case "0", "N", "NO", "FALSE", "F", "", "INACTIVE" -> 0;
            default -> throw new IllegalArgumentException("unrecognised IS_ACTIVE value '" + raw + "'");
        };
    }

    public static BigDecimal parseAmount(String raw) {
        String value = raw.trim().replace(",", "").replace(" ", "");
        try {
            return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount '" + raw + "'");
        }
    }

    /** MONTH is a date; a month-only value is normalised to the first day of that month. */
    public static LocalDate parseMonth(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter format : DAY_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        for (DateTimeFormatter format : MONTH_FORMATS) {
            try {
                return YearMonth.parse(value, format).atDay(1);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        throw new IllegalArgumentException("unrecognised MONTH value '" + raw + "'");
    }
}
