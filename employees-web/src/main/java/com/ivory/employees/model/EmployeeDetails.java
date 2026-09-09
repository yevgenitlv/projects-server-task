package com.ivory.employees.model;

import com.ivory.employees.json.Json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Full employee record: general details, address and (optionally) the salary list. */
public record EmployeeDetails(long code,
                              String name,
                              boolean active,
                              Address address,
                              BigDecimal annualIncome,
                              List<Salary> salaries) {

    /** Copy without the salary list, for callers that did not ask for salaries. */
    public EmployeeDetails withoutSalaries() {
        return new EmployeeDetails(code, name, active, address, annualIncome, List.of());
    }

    public Map<String, Object> toJson(boolean includeSalaries) {
        Map<String, Object> general = Json.object();
        general.put("code", code);
        general.put("name", name);
        general.put("active", active);
        general.put("annualIncome", annualIncome);

        Map<String, Object> json = Json.object();
        json.put("general", general);
        json.put("address", address.toJson());
        if (includeSalaries) {
            json.put("salaries", salaries.stream().map(Salary::toJson).toList());
            json.put("salariesCount", salaries.size());
        }
        return json;
    }
}
