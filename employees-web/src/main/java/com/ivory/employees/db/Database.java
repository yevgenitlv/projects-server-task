package com.ivory.employees.db;

import com.ivory.employees.config.AppConfig;
import org.h2.jdbcx.JdbcConnectionPool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Owns the JDBC connection pool. H2 is embedded and ships its own small pool, so no external
 * pooling library is needed.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());

    private final JdbcConnectionPool pool;
    private final String jdbcUrl;

    public Database(AppConfig config) {
        this.jdbcUrl = config.jdbcUrl();
        this.pool = JdbcConnectionPool.create(config.jdbcUrl(), config.jdbcUser(), config.jdbcPassword());
        this.pool.setMaxConnections(20);
        LOG.info(() -> "JDBC pool created for " + jdbcUrl);
    }

    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() {
        pool.dispose();
        LOG.info(() -> "JDBC pool closed for " + jdbcUrl);
    }
}
