package com.ivory.employees.service;

import com.ivory.employees.cache.TtlCache;
import com.ivory.employees.dao.EmployeeDao;
import com.ivory.employees.json.Json;
import com.ivory.employees.model.EmployeeDetails;
import com.ivory.employees.model.EmployeeQuery;
import com.ivory.employees.model.EmployeeSummary;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Business layer for the employee services.
 *
 * <p>Employee details are served from a {@link TtlCache} whose retention time is
 * {@code employees.cache.ttl.minutes} (2 hours by default, per the specification). The cached entry
 * always holds the salary list, so a request with and a request without salaries share one entry.
 * The employee list is not cached: it depends on filter, sort and range.
 */
public final class EmployeeService {

    private static final Logger LOG = Logger.getLogger(EmployeeService.class.getName());

    private final EmployeeDao employeeDao;
    private final TtlCache<Long, EmployeeDetails> cache;

    public EmployeeService(EmployeeDao employeeDao, Duration cacheTtl) {
        this.employeeDao = employeeDao;
        this.cache = new TtlCache<>(cacheTtl);
        LOG.info(() -> "Employee cache retention time: " + cacheTtl);
    }

    public List<EmployeeSummary> list(EmployeeQuery query) {
        long startedAt = System.nanoTime();
        List<EmployeeSummary> employees = employeeDao.list(query);
        LOG.info(() -> "Employee list [" + query + "] returned " + employees.size() + " row(s) in "
                + millisSince(startedAt) + " ms");
        return employees;
    }

    public long countMatching(EmployeeQuery.Filter filter) {
        return employeeDao.count(filter);
    }

    /**
     * Returns one employee, from the cache when possible.
     *
     * @param includeSalaries when false the salary list is stripped from the answer (the cached
     *                        entry keeps it)
     */
    public Optional<EmployeeDetails> findByCode(long code, boolean includeSalaries) {
        EmployeeDetails cached = cache.get(code);
        if (cached != null) {
            LOG.info(() -> "Employee " + code + " served from cache (age within " + cache.ttl() + ")");
            return Optional.of(includeSalaries ? cached : cached.withoutSalaries());
        }
        long startedAt = System.nanoTime();
        Optional<EmployeeDetails> loaded = employeeDao.findByCode(code);
        loaded.ifPresent(details -> {
            cache.put(code, details);
            LOG.info(() -> "Employee " + code + " loaded from the database in " + millisSince(startedAt)
                    + " ms and cached for " + cache.ttl());
        });
        if (loaded.isEmpty()) {
            LOG.info(() -> "Employee " + code + " does not exist");
        }
        return loaded.map(details -> includeSalaries ? details : details.withoutSalaries());
    }

    /** Drops expired cache entries; called on a schedule by the application. */
    public int purgeCache() {
        return cache.purgeExpired();
    }

    public void invalidateCache() {
        cache.clear();
    }

    /** Cache counters, exposed by the health service. */
    public Map<String, Object> cacheStatistics() {
        Map<String, Object> statistics = Json.object();
        statistics.put("retention", cache.ttl().toString());
        statistics.put("retentionMinutes", cache.ttl().toMinutes());
        statistics.put("entries", cache.size());
        statistics.put("hits", cache.hits());
        statistics.put("misses", cache.misses());
        return statistics;
    }

    private static long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
