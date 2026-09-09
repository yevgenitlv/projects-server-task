package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import com.ivory.employees.service.AuthService;
import com.ivory.employees.util.RequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Enforces the LOGIN requirement: every {@code /api/*} service except login and health needs a
 * valid bearer token. The resolved session is put on the request so servlets can read it.
 */
public class AuthFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class.getName());

    /** Request attribute holding the {@link AuthService.Session} of the caller. */
    public static final String SESSION_ATTRIBUTE = "com.ivory.employees.session";

    private static final String TOKEN_ATTRIBUTE = "com.ivory.employees.token";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Services reachable without a token. */
    private static final Set<String> OPEN_PATHS = Set.of("/api/login", "/api/health");

    private final AuthService authService;
    private final boolean prettyJson;

    public AuthFilter(AuthService authService, boolean prettyJson) {
        this.authService = authService;
        this.prettyJson = prettyJson;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String token = readToken(request).orElse(null);
        request.setAttribute(TOKEN_ATTRIBUTE, token);

        if (OPEN_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AuthService.Session> session = authService.validate(token);
        if (session.isEmpty()) {
            LOG.warning(() -> "Rejected unauthenticated call to " + path);
            reject(response, token == null
                    ? "Missing token. Call POST /api/login first, then send 'Authorization: Bearer <token>'."
                    : "The token is unknown or has expired. Call POST /api/login again.");
            return;
        }

        request.setAttribute(SESSION_ATTRIBUTE, session.get());
        RequestContext.user(session.get().username());
        chain.doFilter(request, response);
    }

    /** The session established for this request, when there is one. */
    public static Optional<AuthService.Session> session(HttpServletRequest request) {
        Object session = request.getAttribute(SESSION_ATTRIBUTE);
        return session instanceof AuthService.Session typed ? Optional.of(typed) : Optional.empty();
    }

    /** The bearer token carried by this request, when there is one. */
    public static Optional<String> token(HttpServletRequest request) {
        Object token = request.getAttribute(TOKEN_ATTRIBUTE);
        return token instanceof String text ? Optional.of(text) : readToken(request);
    }

    private static Optional<String> readToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.of(authorization.substring(BEARER_PREFIX.length()).trim());
        }
        String header = request.getHeader("X-Auth-Token");
        return header == null || header.isBlank() ? Optional.empty() : Optional.of(header.trim());
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> error = Json.object();
        error.put("status", 401);
        error.put("message", message);
        error.put("requestId", RequestContext.requestId());

        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("WWW-Authenticate", "Bearer realm=\"employees\"");
        response.getWriter().write(Json.write(Map.of("error", error), prettyJson));
    }
}
