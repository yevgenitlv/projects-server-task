package com.ivory.employees.web;

import com.ivory.employees.util.RequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Logs every request and its answer, correlated by a short request id that also appears in the
 * error bodies, so a call can be followed from the query to the answer:
 *
 * <pre>
 * 10:15:42.031 INFO [7f3c1a92] [-]     RequestLogFilter - --&gt; GET /api/employees?filter=active from 127.0.0.1
 * 10:15:42.048 INFO [7f3c1a92] [admin] RequestLogFilter - &lt;-- 200 GET /api/employees (17 ms)
 * </pre>
 */
public class RequestLogFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(RequestLogFilter.class.getName());

    /** Parameters whose value must never reach the log. */
    private static final java.util.Set<String> SECRET_PARAMETERS = java.util.Set.of("password", "token");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        RequestContext.begin(UUID.randomUUID().toString().substring(0, 8));
        long startedAt = System.nanoTime();
        String summary = request.getMethod() + ' ' + request.getRequestURI();
        try {
            LOG.info(() -> "--> " + summary + parameters(request) + " from " + request.getRemoteAddr());
            chain.doFilter(request, response);
            LOG.info(() -> "<-- " + response.getStatus() + ' ' + summary + " (" + millisSince(startedAt) + " ms)");
        } catch (IOException | ServletException | RuntimeException e) {
            LOG.log(Level.SEVERE, "<-- failed " + summary + " (" + millisSince(startedAt) + " ms)", e);
            throw e;
        } finally {
            RequestContext.clear();
        }
    }

    private static String parameters(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (parameters.isEmpty()) {
            return "";
        }
        return '?' + parameters.entrySet().stream()
                .map(entry -> entry.getKey() + '=' + (SECRET_PARAMETERS.contains(entry.getKey().toLowerCase())
                        ? "***" : String.join(",", entry.getValue())))
                .collect(Collectors.joining("&"));
    }

    private static long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
