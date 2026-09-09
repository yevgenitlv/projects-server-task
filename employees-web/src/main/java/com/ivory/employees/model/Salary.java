package com.ivory.employees.model;

import com.ivory.employees.json.Json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** One row of the SALARIES table. */
public record Salary(long employeeCode, LocalDate month, BigDecimal gross, BigDecimal tax, BigDecimal total) {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("month", MONTH_FORMAT.format(month));
        json.put("date", month.toString());
        json.put("gross", gross);
        json.put("tax", tax);
        json.put("total", total);
        return json;
    }
}
