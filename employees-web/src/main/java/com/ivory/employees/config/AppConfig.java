package com.ivory.employees.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application settings, resolved from (in order) a JVM system property, an environment variable,
 * then the built-in default. The environment variable name is the property name upper-cased with
 * dots replaced by underscores, e.g. {@code employees.cache.ttl.minutes} -> {@code EMPLOYEES_CACHE_TTL_MINUTES}.
 */
public final class AppConfig {

    public static final String DEFAULT_USERS = "admin:Aa123456!";

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final Path dataDir;
    private final boolean reloadData;
    private final Duration cacheTtl;
    private final Duration sessionTtl;
    private final Map<String, String> seedUsers;
    private final boolean prettyJson;
    private final int port;

    private AppConfig() {
        this.jdbcUrl = get("employees.db.url", "jdbc:h2:file:./data/db/employees;AUTO_SERVER=TRUE");
        this.jdbcUser = get("employees.db.user", "sa");
        this.jdbcPassword = get("employees.db.password", "");
        this.dataDir = Path.of(get("employees.data.dir", "./data"));
        this.reloadData = Boolean.parseBoolean(get("employees.data.reload", "false"));
        this.cacheTtl = Duration.ofMinutes(getLong("employees.cache.ttl.minutes", 120));
        this.sessionTtl = Duration.ofMinutes(getLong("employees.session.ttl.minutes", 60));
        this.seedUsers = parseUsers(get("employees.auth.users", DEFAULT_USERS));
        this.prettyJson = Boolean.parseBoolean(get("employees.json.pretty", "true"));
        this.port = (int) getLong("employees.port", 8080);
    }

    public static AppConfig load() {
        return new AppConfig();
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String jdbcUser() {
        return jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword;
    }

    public Path dataDir() {
        return dataDir;
    }

    /** When true the .dat files are re-imported even if the tables already hold rows. */
    public boolean reloadData() {
        return reloadData;
    }

    /** Retention time of an employee entry in the cache (spec: 2 hours). */
    public Duration cacheTtl() {
        return cacheTtl;
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    /** username -> clear-text password, hashed on first start and stored in APP_USERS. */
    public Map<String, String> seedUsers() {
        return seedUsers;
    }

    public boolean prettyJson() {
        return prettyJson;
    }

    public int port() {
        return port;
    }

    @Override
    public String toString() {
        return "AppConfig[jdbcUrl=" + jdbcUrl
                + ", dataDir=" + dataDir.toAbsolutePath().normalize()
                + ", reloadData=" + reloadData
                + ", cacheTtl=" + cacheTtl
                + ", sessionTtl=" + sessionTtl
                + ", seedUsers=" + seedUsers.keySet()
                + ", prettyJson=" + prettyJson
                + ", port=" + port + ']';
    }

    private static Map<String, String> parseUsers(String raw) {
        Map<String, String> users = new LinkedHashMap<>();
        for (String pair : raw.split(",")) {
            String entry = pair.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("employees.auth.users entries must be 'user:password': " + entry);
            }
            users.put(entry.substring(0, separator).trim(), entry.substring(separator + 1));
        }
        return users;
    }

    private static long getLong(String key, long defaultValue) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number but was '" + value + "'");
        }
    }

    private static String get(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return value == null ? defaultValue : value;
    }
}
