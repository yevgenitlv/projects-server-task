package com.ivory.employees.web;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.db.Database;
import com.ivory.employees.service.AuthService;
import com.ivory.employees.service.EmployeeService;

/** The objects the servlets are wired with; assembled once by {@link WebApp}. */
public record Services(AppConfig config, Database database, EmployeeService employees, AuthService auth) {
}
