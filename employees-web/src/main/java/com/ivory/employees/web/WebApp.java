package com.ivory.employees.web;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.dao.EmployeeDao;
import com.ivory.employees.dao.UserDao;
import com.ivory.employees.db.DataImporter;
import com.ivory.employees.db.Database;
import com.ivory.employees.db.SchemaInitializer;
import com.ivory.employees.service.AuthService;
import com.ivory.employees.service.EmployeeService;
import com.ivory.employees.util.LogFormatter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application bootstrap: builds the database, the services and the HTTP endpoints.
 *
 * <p>Servlets and filters are registered here, programmatically, so exactly the same wiring is used
 * whether the WAR is dropped into a container (this listener is picked up through
 * {@code @WebListener}) or the app is started by {@link com.ivory.employees.Launcher}.
 */
@WebListener
public class WebApp implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(WebApp.class.getName());

    /** Servlet-context attribute holding the {@link Services} of the running application. */
    public static final String SERVICES_ATTRIBUTE = "com.ivory.employees.services";

    private Database database;
    private ScheduledExecutorService housekeeping;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        configureLogging();
        AppConfig config = AppConfig.load();
        LOG.info(() -> "Starting employees-web with " + config);

        database = new Database(config);
        SchemaInitializer.initialize(database);
        new DataImporter(database, config).importIfNeeded();

        EmployeeService employeeService = new EmployeeService(new EmployeeDao(database), config.cacheTtl());
        AuthService authService = new AuthService(new UserDao(database), config);
        Services services = new Services(config, database, employeeService, authService);

        ServletContext context = event.getServletContext();
        context.setAttribute(SERVICES_ATTRIBUTE, services);
        registerEndpoints(context, services);
        startHousekeeping(employeeService, authService);

        LOG.info("employees-web is ready");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (housekeeping != null) {
            housekeeping.shutdownNow();
        }
        if (database != null) {
            database.close();
        }
        LOG.info("employees-web stopped");
    }

    /** Filters run in registration order: log first, then authenticate. */
    private void registerEndpoints(ServletContext context, Services services) {
        FilterRegistration.Dynamic logFilter = context.addFilter("requestLog", new RequestLogFilter());
        logFilter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");

        FilterRegistration.Dynamic authFilter = context.addFilter("auth",
                new AuthFilter(services.auth(), services.config().prettyJson()));
        authFilter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/api/*");

        context.addServlet("apiIndex", new ApiIndexServlet(services)).addMapping("/");
        context.addServlet("auth", new AuthServlet(services))
                .addMapping("/api/login", "/api/logout", "/api/session");
        context.addServlet("employees", new EmployeesServlet(services))
                .addMapping("/api/employees", "/api/employees/*");
        context.addServlet("health", new HealthServlet(services)).addMapping("/api/health");

        LOG.info("Registered services: /, /api/login, /api/logout, /api/session, /api/employees, "
                + "/api/employees/{code}, /api/health");
    }

    /** Periodically drops expired cache entries and sessions instead of letting them pile up. */
    private void startHousekeeping(EmployeeService employeeService, AuthService authService) {
        housekeeping = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "employees-housekeeping");
            thread.setDaemon(true);
            return thread;
        });
        housekeeping.scheduleWithFixedDelay(() -> {
            try {
                int cacheEntries = employeeService.purgeCache();
                int sessions = authService.purgeExpiredSessions();
                if (cacheEntries > 0 || sessions > 0) {
                    LOG.fine(() -> "Housekeeping removed " + cacheEntries + " expired cache entr(ies) and "
                            + sessions + " expired session(s)");
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Housekeeping run failed", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Installs the one-line log format on the root logger, unless the JVM was started with its own
     * {@code java.util.logging.config.file}.
     */
    public static void configureLogging() {
        if (System.getProperty("java.util.logging.config.file") != null) {
            return;
        }
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new LogFormatter());
        handler.setLevel(Level.ALL);
        root.addHandler(handler);

        Level level = Level.parse(System.getProperty("employees.log.level", "INFO"));
        root.setLevel(Level.INFO);
        Logger.getLogger("com.ivory.employees").setLevel(level);
    }
}
