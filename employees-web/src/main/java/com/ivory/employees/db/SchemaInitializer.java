package com.ivory.employees.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * Creates the tables described by the specification, plus the APP_USERS table used by LOGIN and a
 * view that computes each employee's annual income.
 */
public final class SchemaInitializer {

    private static final Logger LOG = Logger.getLogger(SchemaInitializer.class.getName());

    /**
     * DDL executed on every start. SALARIES.MONTH is quoted because MONTH is a reserved word in
     * H2 2.x - quoting keeps the column name exactly as the specification gives it.
     *
     * <p> V_ANNUAL_INCOME defines annual income once - the sum of
     * SALARIES.TOTAL over the employee's most recent year - so the list query (sorting) and the
     * details query agree on it.
     */
    private static final List<String> STATEMENTS = List.of("""
            CREATE TABLE IF NOT EXISTS EMPLOYEES (
                CODE            NUMERIC(18)  NOT NULL,
                NAME            VARCHAR(200) NOT NULL,
                IS_ACTIVE       NUMERIC(1)   NOT NULL DEFAULT 0,
                ADDRESS_STREET  VARCHAR(200),
                ADDRESS_NUMBER  VARCHAR(20),
                ADDRESS_CITY    VARCHAR(100),
                ADDRESS_COUNTRY VARCHAR(100),
                CONSTRAINT PK_EMPLOYEES PRIMARY KEY (CODE)
            )
            """, """
            CREATE TABLE IF NOT EXISTS SALARIES (
                EMPLOYEE_CODE NUMERIC(18)   NOT NULL,
                "MONTH"       DATE          NOT NULL,
                GROSS         NUMERIC(15,2) NOT NULL,
                TAX           NUMERIC(15,2) NOT NULL,
                TOTAL         NUMERIC(15,2) NOT NULL,
                CONSTRAINT PK_SALARIES PRIMARY KEY (EMPLOYEE_CODE, "MONTH"),
                CONSTRAINT FK_SALARIES_EMPLOYEE FOREIGN KEY (EMPLOYEE_CODE) REFERENCES EMPLOYEES (CODE)
            )
            """, """
            CREATE INDEX IF NOT EXISTS IX_SALARIES_EMPLOYEE ON SALARIES (EMPLOYEE_CODE)
            """, """
            CREATE TABLE IF NOT EXISTS APP_USERS (
                USERNAME      VARCHAR(60)  NOT NULL,
                SALT          VARCHAR(64)  NOT NULL,
                PASSWORD_HASH VARCHAR(128) NOT NULL,
                CREATED_AT    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT PK_APP_USERS PRIMARY KEY (USERNAME)
            )
            """, """
            CREATE OR REPLACE VIEW V_ANNUAL_INCOME AS
            SELECT s.EMPLOYEE_CODE AS CODE,
                   latest.LATEST_YEAR AS INCOME_YEAR,
                   SUM(s.TOTAL) AS ANNUAL_INCOME
            FROM SALARIES s
            JOIN (SELECT EMPLOYEE_CODE, MAX(YEAR("MONTH")) AS LATEST_YEAR
                  FROM SALARIES
                  GROUP BY EMPLOYEE_CODE) latest
              ON latest.EMPLOYEE_CODE = s.EMPLOYEE_CODE
             AND YEAR(s."MONTH") = latest.LATEST_YEAR
            GROUP BY s.EMPLOYEE_CODE, latest.LATEST_YEAR
            """);

    private SchemaInitializer() {
    }

    public static void initialize(Database database) {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            for (String ddl : STATEMENTS) {
                statement.execute(ddl);
            }
            LOG.info("Database schema is ready (EMPLOYEES, SALARIES, APP_USERS, V_ANNUAL_INCOME)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize the database schema", e);
        }
    }
}
