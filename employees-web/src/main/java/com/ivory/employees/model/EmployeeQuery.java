package com.ivory.employees.model;

import com.ivory.employees.json.Json;
import com.ivory.employees.util.ApiException;

import java.util.Map;

/**
 * Parameters of an employee-list request: which employees to return, and in which order.
 *
 * @param filter      ALL, ACTIVE, or RANGE of record numbers
 * @param fromRecord  first record number (1-based, inclusive), only used by {@link Filter#RANGE}
 * @param toRecord    last record number (inclusive), only used by {@link Filter#RANGE}
 */
public record EmployeeQuery(Filter filter, long fromRecord, long toRecord, Sort sort, Order order) {

    /** Which employees are returned. */
    public enum Filter {
        /** All employees. */
        ALL,
        /** Only employees whose IS_ACTIVE is 1. */
        ACTIVE,
        /** A range of record numbers within the requested ordering. */
        RANGE
    }

    /** Sort key. */
    public enum Sort {
        /** EMPLOYEES.CODE */
        CODE("e.CODE"),
        /** EMPLOYEES.NAME */
        NAME("e.NAME"),
        /** Sum of SALARIES.TOTAL for the employee's most recent year. */
        ANNUAL_INCOME("ANNUAL_INCOME");

        private final String sqlColumn;

        Sort(String sqlColumn) {
            this.sqlColumn = sqlColumn;
        }

        /** Whitelisted column name - the only value ever interpolated into the ORDER BY clause. */
        public String sqlColumn() {
            return sqlColumn;
        }
    }

    /** Sort direction. */
    public enum Order {
        ASC, DESC;

        public String sqlKeyword() {
            return name();
        }
    }

    public EmployeeQuery {
        if (filter == Filter.RANGE) {
            if (fromRecord < 1) {
                throw ApiException.badRequest("'fromRecord' must be 1 or greater");
            }
            if (toRecord < fromRecord) {
                throw ApiException.badRequest("'toRecord' must be greater than or equal to 'fromRecord'");
            }
        }
    }

    public static EmployeeQuery of(Filter filter, Sort sort, Order order) {
        return new EmployeeQuery(filter, 0, 0, sort, order);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> filterJson = Json.object();
        filterJson.put("type", filter.name().toLowerCase());
        if (filter == Filter.RANGE) {
            filterJson.put("fromRecord", fromRecord);
            filterJson.put("toRecord", toRecord);
        }

        Map<String, Object> sortJson = Json.object();
        sortJson.put("by", switch (sort) {
            case CODE -> "code";
            case NAME -> "name";
            case ANNUAL_INCOME -> "annualIncome";
        });
        sortJson.put("order", order.name().toLowerCase());

        Map<String, Object> json = Json.object();
        json.put("filter", filterJson);
        json.put("sort", sortJson);
        return json;
    }

    @Override
    public String toString() {
        return "filter=" + filter
                + (filter == Filter.RANGE ? "(" + fromRecord + ".." + toRecord + ")" : "")
                + " sort=" + sort + " order=" + order;
    }
}
