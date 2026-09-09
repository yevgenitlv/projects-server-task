package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/** {@code GET /api/health} - liveness plus cache and session counters. Open, no token needed. */
public class HealthServlet extends ApiServlet {

    public HealthServlet(Services services) {
        super(services);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> answer = Json.object();
        answer.put("status", "UP");
        answer.put("database", services.database().jdbcUrl());
        answer.put("employees", services.employees().countMatching(
                com.ivory.employees.model.EmployeeQuery.Filter.ALL));
        answer.put("cache", services.employees().cacheStatistics());
        answer.put("activeSessions", services.auth().activeSessions());
        writeJson(response, 200, answer);
    }
}
