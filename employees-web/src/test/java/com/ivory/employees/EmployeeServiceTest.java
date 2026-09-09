package com.ivory.employees;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.dao.EmployeeDao;
import com.ivory.employees.db.DataImporter;
import com.ivory.employees.db.Database;
import com.ivory.employees.db.SchemaInitializer;
import com.ivory.employees.model.EmployeeDetails;
import com.ivory.employees.model.EmployeeQuery;
import com.ivory.employees.model.EmployeeSummary;
import com.ivory.employees.service.EmployeeService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the query layer against a real (in-memory) H2 database loaded from .dat files,
 * covering the filters and the sort orders required by the specification.
 */
class EmployeeServiceTest {

    private static Database database;
    private static EmployeeService service;
    private static Path dataDir;

    @BeforeAll
    static void startDatabase() throws IOException {
        dataDir = Files.createTempDirectory("employees-test");
        Files.writeString(dataDir.resolve("Employees.dat"), """
                CODE;NAME;IS_ACTIVE;ADDRESS_STREET;ADDRESS_NUMBER;ADDRESS_CITY;ADDRESS_COUNTRY
                300;Carol;1;Jaffa;103;Jerusalem;Israel
                100;Alice;1;Herzl;12;Tel Aviv;Israel
                200;Bob;0;Ha-Nassi;7;Haifa;Israel
                400;Dave;1;Bialik;31;Ramat Gan;Israel
                """);
        Files.writeString(dataDir.resolve("Salaries.dat"), """
                EMPLOYEE_CODE;MONTH;GROSS;TAX;TOTAL
                100;2025-01-01;10000;2000;8000
                100;2025-02-01;10000;2000;8000
                200;2025-01-01;30000;5000;25000
                300;2025-01-01;20000;4000;16000
                300;2024-01-01;90000;9000;81000
                """);

        System.setProperty("employees.db.url", "jdbc:h2:mem:test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        System.setProperty("employees.data.dir", dataDir.toString());
        AppConfig config = AppConfig.load();

        database = new Database(config);
        SchemaInitializer.initialize(database);
        new DataImporter(database, config).importIfNeeded();
        service = new EmployeeService(new EmployeeDao(database), Duration.ofHours(2));
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        database.close();
        System.clearProperty("employees.db.url");
        System.clearProperty("employees.data.dir");
        try (var paths = Files.walk(dataDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void listsAllEmployeesOrderedByCode() {
        List<EmployeeSummary> employees = service.list(EmployeeQuery.of(
                EmployeeQuery.Filter.ALL, EmployeeQuery.Sort.CODE, EmployeeQuery.Order.ASC));

        assertEquals(List.of(100L, 200L, 300L, 400L), employees.stream().map(EmployeeSummary::code).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), employees.stream().map(EmployeeSummary::recordNumber).toList());
    }

    @Test
    void listsOnlyActiveEmployees() {
        List<EmployeeSummary> employees = service.list(EmployeeQuery.of(
                EmployeeQuery.Filter.ACTIVE, EmployeeQuery.Sort.CODE, EmployeeQuery.Order.ASC));

        assertEquals(List.of(100L, 300L, 400L), employees.stream().map(EmployeeSummary::code).toList());
        assertTrue(employees.stream().allMatch(EmployeeSummary::active));
    }

    @Test
    void listsARangeOfRecordNumbersWithinTheRequestedOrdering() {
        List<EmployeeSummary> employees = service.list(new EmployeeQuery(
                EmployeeQuery.Filter.RANGE, 2, 3, EmployeeQuery.Sort.CODE, EmployeeQuery.Order.ASC));

        assertEquals(List.of(200L, 300L), employees.stream().map(EmployeeSummary::code).toList());
        assertEquals(List.of(2L, 3L), employees.stream().map(EmployeeSummary::recordNumber).toList(),
                "record numbers keep their position in the full ordering");
    }

    @Test
    void sortsByNameAndByAnnualIncome() {
        List<EmployeeSummary> byName = service.list(EmployeeQuery.of(
                EmployeeQuery.Filter.ALL, EmployeeQuery.Sort.NAME, EmployeeQuery.Order.DESC));
        assertEquals(List.of("Dave", "Carol", "Bob", "Alice"), byName.stream().map(EmployeeSummary::name).toList());

        // 2025 income: Bob 25000, Alice 16000, Carol 16000 (her 2024 rows do not count), Dave 0.
        // Alice and Carol tie, so the secondary sort on CODE decides - the ordering is deterministic.
        List<EmployeeSummary> byIncome = service.list(EmployeeQuery.of(
                EmployeeQuery.Filter.ALL, EmployeeQuery.Sort.ANNUAL_INCOME, EmployeeQuery.Order.DESC));
        assertEquals(List.of(200L, 100L, 300L, 400L), byIncome.stream().map(EmployeeSummary::code).toList());
        assertEquals(0, byIncome.get(0).annualIncome().compareTo(new java.math.BigDecimal("25000")));
        assertEquals(0, byIncome.get(3).annualIncome().compareTo(java.math.BigDecimal.ZERO),
                "an employee with no salary rows has an annual income of 0");
    }

    @Test
    void annualIncomeCountsTheMostRecentYearOnly() {
        EmployeeDetails carol = service.findByCode(300, true).orElseThrow();

        assertEquals(0, carol.annualIncome().compareTo(new java.math.BigDecimal("16000")),
                "2024 salaries must not be added to the 2025 total");
        assertEquals(2, carol.salaries().size(), "the salary list itself keeps every month");
    }

    @Test
    void returnsGeneralDetailsAddressAndOptionallySalaries() {
        EmployeeDetails withSalaries = service.findByCode(100, true).orElseThrow();
        assertEquals("Alice", withSalaries.name());
        assertEquals("Tel Aviv", withSalaries.address().city());
        assertEquals("12", withSalaries.address().number());
        assertEquals(2, withSalaries.salaries().size());

        EmployeeDetails withoutSalaries = service.findByCode(100, false).orElseThrow();
        assertTrue(withoutSalaries.salaries().isEmpty());
        assertFalse(withoutSalaries.toJson(false).containsKey("salaries"));
    }

    @Test
    void secondReadOfTheSameEmployeeIsServedFromTheCache() {
        service.invalidateCache();
        long missesBefore = (long) service.cacheStatistics().get("misses");

        service.findByCode(400, false);
        service.findByCode(400, true);

        assertEquals(missesBefore + 1, (long) service.cacheStatistics().get("misses"),
                "only the first read reaches the database");
        assertEquals(1L, (long) service.cacheStatistics().get("hits"));
        assertEquals(1, (int) service.cacheStatistics().get("entries"));
    }

    @Test
    void unknownEmployeeIsReportedAsAbsent() {
        assertTrue(service.findByCode(999, true).isEmpty());
    }

    @Test
    void rejectsAnInvalidRecordRange() {
        assertThrows(com.ivory.employees.util.ApiException.class, () -> new EmployeeQuery(
                EmployeeQuery.Filter.RANGE, 5, 2, EmployeeQuery.Sort.CODE, EmployeeQuery.Order.ASC));
    }
}
