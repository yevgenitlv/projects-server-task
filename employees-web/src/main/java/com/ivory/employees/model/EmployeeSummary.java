package com.ivory.employees.model;

import com.ivory.employees.json.Json;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A row of the employee list.
 *
 * @param recordNumber 1-based position of the row within the requested ordering; this is what the
 *                     "record number range" filter selects on
 * @param annualIncome sum of SALARIES.TOTAL for the employee's most recent year in the data
 */
public record EmployeeSummary(long recordNumber, long code, String name, boolean active, BigDecimal annualIncome) {

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("recordNumber", recordNumber);
        json.put("code", code);
        json.put("name", name);
        json.put("active", active);
        json.put("annualIncome", annualIncome);
        return json;
    }
}
