package com.ivory.employees.dao;

import com.ivory.employees.db.Database;
import com.ivory.employees.model.Address;
import com.ivory.employees.model.EmployeeDetails;
import com.ivory.employees.model.EmployeeQuery;
import com.ivory.employees.model.EmployeeSummary;
import com.ivory.employees.model.Salary;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Plain-JDBC access to EMPLOYEES / SALARIES. */
public final class EmployeeDao {

    private static final Logger LOG = Logger.getLogger(EmployeeDao.class.getName());

    private static final String LIST_SELECT = """
            SELECT e.CODE, e.NAME, e.IS_ACTIVE, COALESCE(a.ANNUAL_INCOME, 0) AS ANNUAL_INCOME
            FROM EMPLOYEES e
            LEFT JOIN V_ANNUAL_INCOME a ON a.CODE = e.CODE
            """;

    private static final String DETAILS_SELECT = """
            SELECT e.CODE, e.NAME, e.IS_ACTIVE, e.ADDRESS_STREET, e.ADDRESS_NUMBER,
                   e.ADDRESS_CITY, e.ADDRESS_COUNTRY, COALESCE(a.ANNUAL_INCOME, 0) AS ANNUAL_INCOME
            FROM EMPLOYEES e
            LEFT JOIN V_ANNUAL_INCOME a ON a.CODE = e.CODE
            WHERE e.CODE = ?
            """;

    private static final String SALARIES_SELECT = """
            SELECT EMPLOYEE_CODE, "MONTH", GROSS, TAX, TOTAL
            FROM SALARIES
            WHERE EMPLOYEE_CODE = ?
            ORDER BY "MONTH"
            """;

    private final Database database;

    public EmployeeDao(Database database) {
        this.database = database;
    }

    /**
     * Runs an employee-list query. The sort key and direction come from enums, so only whitelisted
     * identifiers ever reach the ORDER BY clause; every user-supplied value is bound as a parameter.
     */
    public List<EmployeeSummary> list(EmployeeQuery query) {
        StringBuilder sql = new StringBuilder(LIST_SELECT);
        if (query.filter() == EmployeeQuery.Filter.ACTIVE) {
            sql.append("WHERE e.IS_ACTIVE = 1\n");
        }
        sql.append("ORDER BY ").append(query.sort().sqlColumn()).append(' ').append(query.order().sqlKeyword());
        if (query.sort() != EmployeeQuery.Sort.CODE) {
            sql.append(", e.CODE ASC");
        }
        boolean ranged = query.filter() == EmployeeQuery.Filter.RANGE;
        if (ranged) {
            sql.append("\nOFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        }

        String statementSql = sql.toString();
        LOG.fine(() -> "Employee list query [" + query + "]: " + statementSql.replace('\n', ' '));

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(statementSql)) {
            long firstRecord = 1;
            if (ranged) {
                firstRecord = query.fromRecord();
                statement.setLong(1, query.fromRecord() - 1);
                statement.setLong(2, query.toRecord() - query.fromRecord() + 1);
            }
            List<EmployeeSummary> employees = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                long recordNumber = firstRecord;
                while (resultSet.next()) {
                    employees.add(new EmployeeSummary(
                            recordNumber++,
                            resultSet.getLong("CODE"),
                            resultSet.getString("NAME"),
                            resultSet.getInt("IS_ACTIVE") == 1,
                            resultSet.getBigDecimal("ANNUAL_INCOME")));
                }
            }
            return employees;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list employees (" + query + ")", e);
        }
    }

    /** Total number of employees matching the filter, ignoring any record-number range. */
    public long count(EmployeeQuery.Filter filter) {
        String sql = "SELECT COUNT(*) FROM EMPLOYEES"
                + (filter == EmployeeQuery.Filter.ACTIVE ? " WHERE IS_ACTIVE = 1" : "");
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count employees", e);
        }
    }

    /** Loads one employee with the full salary list attached. */
    public Optional<EmployeeDetails> findByCode(long code) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(DETAILS_SELECT)) {
            statement.setLong(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Address address = new Address(
                        resultSet.getString("ADDRESS_STREET"),
                        resultSet.getString("ADDRESS_NUMBER"),
                        resultSet.getString("ADDRESS_CITY"),
                        resultSet.getString("ADDRESS_COUNTRY"));
                EmployeeDetails details = new EmployeeDetails(
                        resultSet.getLong("CODE"),
                        resultSet.getString("NAME"),
                        resultSet.getInt("IS_ACTIVE") == 1,
                        address,
                        resultSet.getBigDecimal("ANNUAL_INCOME"),
                        salaries(connection, code));
                return Optional.of(details);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load employee " + code, e);
        }
    }

    private List<Salary> salaries(Connection connection, long code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SALARIES_SELECT)) {
            statement.setLong(1, code);
            List<Salary> salaries = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    salaries.add(new Salary(
                            resultSet.getLong("EMPLOYEE_CODE"),
                            resultSet.getDate("MONTH").toLocalDate(),
                            amount(resultSet.getBigDecimal("GROSS")),
                            amount(resultSet.getBigDecimal("TAX")),
                            amount(resultSet.getBigDecimal("TOTAL"))));
                }
            }
            return List.copyOf(salaries);
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
