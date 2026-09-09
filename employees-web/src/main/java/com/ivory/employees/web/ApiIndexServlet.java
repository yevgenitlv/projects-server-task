package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import com.ivory.employees.util.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** {@code GET /} - a self-describing index of the services, so the API can be explored. */
public class ApiIndexServlet extends ApiServlet {

    public ApiIndexServlet(Services services) {
        super(services);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/".equals(request.getRequestURI().substring(request.getContextPath().length()))) {
            throw ApiException.notFound("Unknown service " + request.getRequestURI());
        }
        Map<String, Object> answer = Json.object();
        answer.put("service", "employees-web");
        answer.put("authentication", "POST /api/login, then send 'Authorization: Bearer <token>'");
        answer.put("services", List.of(
                endpoint("POST", "/api/login", "Log in; body {\"username\":\"...\",\"password\":\"...\"}"),
                endpoint("POST", "/api/logout", "End the current session"),
                endpoint("GET", "/api/session", "Describe the current session"),
                endpoint("GET", "/api/employees",
                        "Employee list; filter=all|active|range, fromRecord, toRecord, "
                                + "sort=code|name|annualIncome, order=asc|desc"),
                endpoint("GET", "/api/employees/{code}",
                        "One employee: general details and address; includeSalaries=true adds the salary list"),
                endpoint("GET", "/api/health", "Liveness, cache and session counters")));
        writeJson(response, 200, answer);
    }

    private static Map<String, Object> endpoint(String method, String path, String description) {
        Map<String, Object> json = Json.object();
        json.put("method", method);
        json.put("path", path);
        json.put("description", description);
        return json;
    }
}
