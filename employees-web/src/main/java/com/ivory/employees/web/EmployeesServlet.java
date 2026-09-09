package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import com.ivory.employees.model.EmployeeDetails;
import com.ivory.employees.model.EmployeeQuery;
import com.ivory.employees.model.EmployeeSummary;
import com.ivory.employees.util.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The employee services.
 *
 * <p>{@code GET /api/employees} - the employee list.
 * <ul>
 *   <li>{@code filter=all} (default) | {@code active} | {@code range}</li>
 *   <li>{@code fromRecord} / {@code toRecord} - 1-based, inclusive; required by {@code filter=range}.
 *       Record numbers are positions inside the requested ordering.</li>
 *   <li>{@code sort=code} (default) | {@code name} | {@code annualIncome}</li>
 *   <li>{@code order=asc} (default) | {@code desc}</li>
 * </ul>
 *
 * <p>{@code GET /api/employees/{code}} - one employee: general details and address, plus the salary
 * list when {@code includeSalaries=true}. Served from the cache when the entry is still fresh.
 */
public class EmployeesServlet extends ApiServlet {

    public EmployeesServlet(Services services) {
        super(services);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            listEmployees(request, response);
        } else {
            employeeDetails(pathInfo, request, response);
        }
    }

    private void listEmployees(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EmployeeQuery.Filter filter = enumParam(request, "filter", EmployeeQuery.Filter.class,
                EmployeeQuery.Filter.ALL);
        EmployeeQuery.Sort sort = enumParam(request, "sort", EmployeeQuery.Sort.class,
                EmployeeQuery.Sort.CODE);
        EmployeeQuery.Order order = enumParam(request, "order", EmployeeQuery.Order.class,
                EmployeeQuery.Order.ASC);

        long fromRecord = 0;
        long toRecord = 0;
        if (filter == EmployeeQuery.Filter.RANGE) {
            fromRecord = longParam(request, "fromRecord", -1);
            toRecord = longParam(request, "toRecord", -1);
            if (fromRecord < 0 || toRecord < 0) {
                throw ApiException.badRequest("'filter=range' needs both 'fromRecord' and 'toRecord'");
            }
        }

        EmployeeQuery query = new EmployeeQuery(filter, fromRecord, toRecord, sort, order);
        List<EmployeeSummary> employees = services.employees().list(query);

        Map<String, Object> answer = Json.object();
        answer.putAll(query.toJson());
        answer.put("returned", employees.size());
        answer.put("totalMatching", services.employees().countMatching(filter));
        answer.put("employees", employees.stream().map(EmployeeSummary::toJson).toList());
        writeJson(response, 200, answer);
    }

    private void employeeDetails(String pathInfo, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String rawCode = pathInfo.substring(1);
        if (rawCode.endsWith("/")) {
            rawCode = rawCode.substring(0, rawCode.length() - 1);
        }
        if (rawCode.isEmpty() || rawCode.contains("/")) {
            throw ApiException.notFound("Unknown service /api/employees" + pathInfo);
        }
        long code;
        try {
            code = Long.parseLong(rawCode);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("The employee code must be numeric but was '" + rawCode + "'");
        }

        boolean includeSalaries = booleanParam(request, "includeSalaries", false);
        EmployeeDetails employee = services.employees().findByCode(code, includeSalaries)
                .orElseThrow(() -> ApiException.notFound("No employee with code " + code));

        Map<String, Object> answer = Json.object();
        answer.put("employee", employee.toJson(includeSalaries));
        answer.put("includeSalaries", includeSalaries);
        writeJson(response, 200, answer);
    }
}
